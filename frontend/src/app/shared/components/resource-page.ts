import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { LowerCasePipe } from '@angular/common';
import { forkJoin, of, map, switchMap, catchError, Observable } from 'rxjs';
import { FormBuilder, FormGroup, ReactiveFormsModule, ValidatorFn, Validators } from '@angular/forms';
import { CrudService } from '../../core/services/crud.service';
import { ToastService } from '../../core/services/toast.service';
import { AuthService } from '../../core/auth/auth.service';
import { getResource } from '../../core/registry';
import {
  ColumnFilterConfig,
  FieldConfig,
  FilterConfig,
  PatchAction,
  ResourceConfig,
  RowEditorConfig,
} from '../crud/resource-config';
import { HasId } from '../../core/models/dtos';
import { LabelizePipe, labelize } from '../pipes/labelize.pipe';
import { formatRupees } from '../money';
import { SelectValueDirective } from '../ui/select-value';

/**
 * Text longer than this is ellipsised in the table and gets a "read it all"
 * button. Chosen to match .se-truncate's 260px column cap — below it the cell
 * shows the whole value anyway, so offering to expand would be noise.
 */
const TRUNCATE_AT = 60;

type Row = Record<string, unknown> & HasId;
interface RefOption {
  value: number;
  label: string;
}

/**
 * One component renders the full CRUD screen (list, filters, search, paging,
 * create/edit modal, delete confirm, and any extra PATCH actions) for ANY
 * resource. The behaviour is driven entirely by the ResourceConfig handed in
 * via the route's `data.config`.
 */
@Component({
  selector: 'app-resource-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, LabelizePipe, LowerCasePipe, SelectValueDirective],
  templateUrl: './resource-page.html',
})
export class ResourcePageComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private crud = inject(CrudService);
  private toast = inject(ToastService);
  private auth = inject(AuthService);
  private fb = inject(FormBuilder);

  config!: ResourceConfig;
  formFields: FieldConfig[] = [];

  readonly rows = signal<Row[]>([]);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly refOptions = signal<Record<string, RefOption[]>>({});
  /** For managerScope resources: the allowed id set + which row field to test
   *  it against ('propertyId' or 'id'). null = no client-side scoping. */
  private readonly scopeIds = signal<Set<number> | null>(null);
  private readonly scopeField = signal<string>('propertyId');

  filterForm!: FormGroup;
  readonly search = signal('');
  readonly page = signal(1);
  readonly pageSize = 10;

  // Modal state
  readonly modalOpen = signal(false);
  readonly editingId = signal<number | null>(null);
  form!: FormGroup;
  readonly deleteTarget = signal<Row | null>(null);

  // Whether required filters are all satisfied (else the list stays gated).
  // NOTE: this MUST be a method, not a computed() — it reads reactive-form
  // values (not signals), so a computed would memoize its first result forever
  // and the list would never un-gate once a required filter is chosen.
  gated(): boolean {
    const missing = (this.config?.filters ?? []).filter((f) => f.required);
    return missing.some((f) => {
      const v = this.filterForm?.get(f.key)?.value;
      return v === null || v === undefined || v === '';
    });
  }

  /** Chosen value per column-header dropdown ('' / missing = All). */
  readonly columnFilterValues = signal<Record<string, string>>({});

  readonly filteredRows = computed(() => {
    const picks = this.columnFilterValues();
    const active = Object.entries(picks).filter(([, v]) => v !== '' && v !== undefined);
    let rows = this.rows();
    if (active.length) {
      // Matched against the DISPLAYED text so a header dropdown can offer
      // reader-facing options (e.g. "Verified") for several stored enum values.
      rows = rows.filter((r) => active.every(([col, want]) => this.display(r, col) === want));
    }
    const term = this.search().trim().toLowerCase();
    if (!term) {
      return rows;
    }
    return rows.filter((r) => JSON.stringify(r).toLowerCase().includes(term));
  });

  readonly pageCount = computed(() => Math.max(1, Math.ceil(this.filteredRows().length / this.pageSize)));

  readonly pagedRows = computed(() => {
    const start = (this.page() - 1) * this.pageSize;
    return this.filteredRows().slice(start, start + this.pageSize);
  });

  ngOnInit(): void {
    this.config = this.route.snapshot.data['config'] as ResourceConfig;
    this.formFields = this.config.fields.filter((f) => !f.hideInForm);

    // Build the filter form.
    const filterControls: Record<string, unknown> = {};
    for (const f of this.config.filters ?? []) {
      filterControls[f.key] = [''];
    }
    this.filterForm = this.fb.group(filterControls);

    this.loadReferenceOptions();

    // managerScope resources aren't scoped by the backend, so a manager would
    // see every property's rows / every guest. Resolve the allowed id set first,
    // then load and filter the list client-side.
    if (this.config.managerScope && this.auth.role() === 'PROPERTY_MANAGER') {
      this.managerScopeData(this.config.managerScope).subscribe((res) => {
        this.scopeField.set(res.field);
        this.scopeIds.set(res.ids);
        if (!this.gated()) this.load();
      });
    } else if (!this.gated()) {
      this.load();
    }
  }

  /**
   * Compute the ids a PROPERTY_MANAGER is allowed to see, plus which row field
   * to match them against:
   *   'property'         → the manager's own property ids, matched on propertyId.
   *   'reservationGuest' → guest ids drawn from reservations at those properties,
   *                        matched on the guest row's id.
   */
  private managerScopeData(
    mode: 'property' | 'reservation' | 'reservationGuest',
  ): Observable<{ field: string; ids: Set<number> }> {
    return this.crud.list<Row>('/api/properties').pipe(
      switchMap((props) => {
        const propIds = new Set(props.map((p) => Number(p.id)));
        if (mode === 'property') {
          return of({ field: 'propertyId', ids: propIds });
        }
        // 'reservation' and 'reservationGuest' both start from the manager's
        // reservations (those at their properties).
        return this.crud.list<Row>('/api/reservations').pipe(
          map((res) => {
            const mine = res.filter((r) => propIds.has(Number(r['propertyId'])));
            return mode === 'reservation'
              ? { field: 'reservationId', ids: new Set(mine.map((r) => Number(r.id))) }
              : { field: 'id', ids: new Set(mine.map((r) => Number(r['guestId']))) };
          }),
          catchError(() =>
            of({ field: mode === 'reservation' ? 'reservationId' : 'id', ids: new Set<number>() }),
          ),
        );
      }),
      catchError(() => of({ field: mode === 'property' ? 'propertyId' : mode === 'reservation' ? 'reservationId' : 'id', ids: new Set<number>() })),
    );
  }

  // ---------------- data loading ----------------
  load(): void {
    if (this.gated()) {
      return;
    }
    this.loading.set(true);
    this.page.set(1);
    const params = { ...this.filterForm.value } as Record<string, string | number>;
    // Force any per-role scope (e.g. a manager only sees their own properties).
    const role = this.auth.role();
    const scope = role ? this.config.roleScope?.[role] : undefined;
    if (scope?.value === 'userId') {
      const uid = this.auth.user()?.userId;
      if (uid != null) {
        params[scope.param] = uid;
      }
    }
    this.crud.list<Row>(this.config.apiBase, params).subscribe({
      next: (data) => {
        // Client-side manager scoping: keep only rows in the allowed id set.
        const scope = this.scopeIds();
        const field = this.scopeField();
        const rows = scope ? data.filter((r) => scope.has(Number(r[field]))) : data;
        this.rows.set(rows);
        this.fillMissingRefOptions(rows);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  /**
   * Resolve reference labels for ids that the (manager-scoped) options list
   * doesn't contain. A PROPERTY_MANAGER's /api/properties list is limited to
   * their own properties, so a property referenced by another resource's row
   * (e.g. a reservation) would show "#id". We fetch those by id (unscoped) and
   * merge them in. Only runs for managers and only for references whose target
   * is itself manager-scoped, so it makes no extra calls otherwise.
   */
  private fillMissingRefOptions(rows: Row[]): void {
    if (this.auth.role() !== 'PROPERTY_MANAGER') {
      return;
    }
    const refFields = this.config.fields.filter((f) => f.type === 'reference' && f.ref);
    for (const f of refFields) {
      const key = f.ref!.resourceKey;
      const target = getResource(key);
      if (!target || !target.roleScope?.['PROPERTY_MANAGER']) {
        continue; // only a scoped reference list can be missing ids
      }
      const have = new Set(this.optionsForResource(key).map((o) => o.value));
      const missing = [
        ...new Set(rows.map((r) => Number(r[f.key])).filter((v) => Number.isFinite(v) && v > 0 && !have.has(v))),
      ];
      if (!missing.length) {
        continue;
      }
      const labelFields = f.ref!.labelFields;
      forkJoin(
        missing.map((id) => this.crud.get<Row>(target.apiBase, id).pipe(catchError(() => of(null)))),
      ).subscribe((recs) => {
        const add: RefOption[] = [];
        for (const rec of recs) {
          if (!rec) {
            continue;
          }
          const parts = labelFields.map((lf) => rec[lf]).filter((v) => v !== undefined && v !== null && v !== '');
          add.push({ value: rec.id, label: parts.length ? parts.join(' · ') : `#${rec.id}` });
        }
        if (add.length) {
          this.mergeRefOptions(key, add);
        }
      });
    }
  }

  /** Merge options by value so async list + by-id resolution don't clobber each other. */
  private mergeRefOptions(key: string, opts: RefOption[]): void {
    this.refOptions.update((m) => {
      const byVal = new Map((m[key] ?? []).map((o) => [o.value, o]));
      for (const o of opts) {
        byVal.set(o.value, o);
      }
      return { ...m, [key]: [...byVal.values()] };
    });
  }

  applyFilters(): void {
    this.load();
  }

  clearFilters(): void {
    this.filterForm.reset();
    // "Clear" means clear everything the user narrowed down by, including the
    // per-column header pickers — otherwise a hidden column filter survives it.
    this.columnFilterValues.set({});
    this.rows.set([]);
    if (!this.gated()) {
      this.load();
    }
  }

  private loadReferenceOptions(): void {
    const refKeys = new Set<string>();
    const collect = (items: (FieldConfig | FilterConfig)[]) => {
      for (const it of items) {
        if (it.type === 'reference' && it.ref) {
          refKeys.add(it.ref.resourceKey);
        }
      }
    };
    collect(this.config.fields);
    collect(this.config.filters ?? []);

    for (const key of refKeys) {
      const target = getResource(key);
      if (!target) {
        continue;
      }
      const labelFields = this.labelFieldsFor(key);
      this.crud.list<Row>(target.apiBase).subscribe({
        next: (data) => {
          const opts: RefOption[] = data.map((r) => {
            // Show the meaningful label (e.g. title · city, or a name); fall back
            // to the id only when the record has no descriptive fields.
            const parts = labelFields
              .map((lf) => r[lf])
              .filter((v) => v !== undefined && v !== null && v !== '');
            return { value: r.id, label: parts.length ? parts.join(' · ') : `#${r.id}` };
          });
          this.mergeRefOptions(key, opts);
        },
        // A 403/empty just means we fall back to a plain number input.
        error: () => {},
      });
    }
  }

  private labelFieldsFor(resourceKey: string): string[] {
    const all = [...this.config.fields, ...(this.config.filters ?? [])];
    for (const it of all) {
      if (it.type === 'reference' && it.ref?.resourceKey === resourceKey) {
        return it.ref.labelFields;
      }
    }
    return [];
  }

  optionsForResource(resourceKey: string | undefined): RefOption[] {
    return resourceKey ? this.refOptions()[resourceKey] ?? [] : [];
  }

  /**
   * Write a native <select>'s chosen value into its reactive control.
   *
   * The selects are bound through SelectValueDirective rather than
   * formControlName because a formControlName <select> can drop the first choice
   * in this zoneless app (reference options also arrive async), leaving required
   * fields looking unselected. The control still owns validation and submission;
   * we only feed it the value reliably.
   */
  writeSelect(scope: 'form' | 'filter', key: string, value: string): void {
    const group = scope === 'filter' ? this.filterForm : this.form;
    group?.get(key)?.setValue(value);
  }

  /**
   * The current control value as a string the <select> can match against an
   * option. Numeric reference ids are stringified so `[seSelectValue]` compares
   * like-for-like with `<option [value]="o.value">`.
   *
   * This pair of readers is what makes the selection STICK: the directive
   * re-asserts this value onto the element after every render, so a late-arriving
   * options list (or any re-render of the modal) can no longer leave the browser
   * showing option 0 while the control holds the real choice.
   */
  selectValue(scope: 'form' | 'filter', key: string): string {
    const group = scope === 'filter' ? this.filterForm : this.form;
    const value = group?.get(key)?.value;
    return value === null || value === undefined ? '' : String(value);
  }

  // ---------------- create / edit ----------------
  openCreate(): void {
    this.editingId.set(null);
    this.form = this.buildForm(null);
    // Seed the form from any active filters so, e.g., creating a checklist while
    // viewing a turnover defaults to that turnover.
    for (const f of this.config.filters ?? []) {
      const fv = this.filterForm.get(f.key)?.value;
      if (fv !== null && fv !== undefined && fv !== '' && this.form.get(f.key)) {
        this.form.get(f.key)!.setValue(fv);
      }
    }
    this.modalOpen.set(true);
  }

  openEdit(row: Row): void {
    this.editingId.set(row.id);
    this.form = this.buildForm(row);
    this.modalOpen.set(true);
  }

  closeModal(): void {
    this.modalOpen.set(false);
  }

  private buildForm(row: Row | null): FormGroup {
    const controls: Record<string, unknown> = {};
    for (const f of this.formFields) {
      const validators: ValidatorFn[] = [];
      if (f.required) validators.push(Validators.required);
      if (f.type === 'email') validators.push(Validators.email);
      if (f.min !== undefined) validators.push(Validators.min(f.min));
      if (f.max !== undefined) validators.push(Validators.max(f.max));
      if (f.maxLength !== undefined) validators.push(Validators.maxLength(f.maxLength));

      let initial: unknown;
      if (row) {
        initial = this.toFormValue(f, row[f.key]);
      } else {
        initial = f.type === 'boolean' ? false : '';
      }
      controls[f.key] = [initial, validators];
    }
    return this.fb.group(controls);
  }

  private toFormValue(f: FieldConfig, value: unknown): unknown {
    if (value === null || value === undefined) {
      return f.type === 'boolean' ? false : '';
    }
    if (f.type === 'time') {
      return String(value).slice(0, 5); // HH:mm
    }
    if (f.type === 'datetime') {
      return String(value).slice(0, 16); // yyyy-MM-ddTHH:mm
    }
    return value;
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const payload: Record<string, unknown> = {};
    for (const f of this.formFields) {
      const raw = this.form.get(f.key)!.value;
      if (f.type === 'boolean') {
        payload[f.key] = !!raw;
        continue;
      }
      if (raw === null || raw === undefined || raw === '') {
        continue; // omit empty optionals so the backend applies its defaults
      }
      if (f.type === 'number' || f.type === 'money' || f.type === 'reference') {
        payload[f.key] = Number(raw);
      } else {
        payload[f.key] = raw;
      }
    }

    this.saving.set(true);
    const id = this.editingId();
    const req$ =
      id === null
        ? this.crud.create<Row>(this.config.apiBase, payload)
        : this.crud.update<Row>(this.config.apiBase, id, payload);

    req$.subscribe({
      next: () => {
        this.saving.set(false);
        this.modalOpen.set(false);
        this.toast.success(`${this.config.singular} ${id === null ? 'created' : 'updated'}.`);
        // Make the saved row visible even when the list is gated by a required
        // filter (e.g. checklists require a turnoverId): adopt the item's value.
        for (const f of this.config.filters ?? []) {
          if (f.required && payload[f.key] !== null && payload[f.key] !== undefined && payload[f.key] !== '') {
            this.filterForm.get(f.key)?.setValue(payload[f.key]);
          }
        }
        this.load();
      },
      error: () => this.saving.set(false),
    });
  }

  // ---------------- delete ----------------
  askDelete(row: Row): void {
    this.deleteTarget.set(row);
  }

  cancelDelete(): void {
    this.deleteTarget.set(null);
  }

  confirmDelete(): void {
    const row = this.deleteTarget();
    if (!row) {
      return;
    }
    this.crud.remove(this.config.apiBase, row.id).subscribe({
      next: () => {
        this.deleteTarget.set(null);
        this.toast.success(`${this.config.singular} deleted.`);
        this.load();
      },
      error: () => this.deleteTarget.set(null),
    });
  }

  // ---------------- patch actions ----------------
  runPatch(action: PatchAction, row: Row): void {
    this.crud.patch(`${this.config.apiBase}/${row.id}${action.suffix}`).subscribe({
      next: () => {
        this.toast.success(action.label + ' done.');
        this.load();
      },
    });
  }

  visiblePatchActions(row: Row): PatchAction[] {
    return (this.config.patchActions ?? []).filter((a) => !a.showWhen || a.showWhen(row));
  }

  /** True when this resource is view-only — globally, or for the current role. */
  isReadOnly(): boolean {
    if (this.config.readOnly) {
      return true;
    }
    const role = this.auth.role();
    return !!(role && this.config.readOnlyRoles?.includes(role));
  }

  // ---------------- table helpers ----------------
  fieldFor(column: string): FieldConfig | undefined {
    return this.config.fields.find((f) => f.key === column);
  }

  columnLabel(column: string): string {
    if (column === 'id') return 'ID';
    const f = this.fieldFor(column);
    return f ? f.label : column;
  }

  display(row: Row, column: string): string {
    const value = row[column];
    const f = this.fieldFor(column);
    if (value === null || value === undefined || value === '') {
      return '—';
    }
    if (f?.valueLabels) {
      const mapped = f.valueLabels[String(value)];
      if (mapped !== undefined) {
        return mapped;
      }
    }
    if (f?.type === 'reference' && f.ref) {
      const opt = this.optionsForResource(f.ref.resourceKey).find((o) => o.value === Number(value));
      return opt ? opt.label : `#${value}`;
    }
    if (f?.type === 'money') {
      return this.money(value);
    }
    if (f?.type === 'datetime') {
      return String(value).replace('T', ' ').slice(0, 16);
    }
    if (f?.type === 'select') {
      return labelize(value);
    }
    return String(value);
  }

  // ---------------- column-header filters ----------------
  columnFilterFor(column: string): ColumnFilterConfig | undefined {
    return this.config.columnFilters?.find((c) => c.key === column);
  }

  columnFilterValue(column: string): string {
    return this.columnFilterValues()[column] ?? '';
  }

  onColumnFilter(column: string, value: string): void {
    this.columnFilterValues.update((m) => ({ ...m, [column]: value }));
    this.page.set(1);
  }

  // ---------------- editable per-row dropdowns ----------------

  /** The row-editor for a column, when the current role may actually change it. */
  rowEditorFor(column: string): RowEditorConfig | undefined {
    const editor = this.config.rowEditors?.find((e) => e.key === column);
    if (!editor) {
      return undefined;
    }
    const role = this.auth.role();
    // roles is an explicit allow-list because the resource may be read-only for
    // this role overall (Guests is, for a manager) while this one field is not.
    return !editor.roles || (role && editor.roles.includes(role)) ? editor : undefined;
  }

  rowEditorValue(editor: RowEditorConfig, row: Row): string {
    const current = editor.fromRow(row);
    // Fall back to the first option so a row with no stored value still shows a
    // definite answer (verification defaults to Unverified) rather than a blank.
    return editor.options.includes(current) ? current : editor.options[0];
  }

  /** Rows currently being saved by a row-editor, so we can disable those selects. */
  readonly savingRows = signal<Set<number>>(new Set());

  isRowSaving(row: Row): boolean {
    return this.savingRows().has(row.id);
  }

  /**
   * Persist a row-editor pick immediately.
   *
   * Sent as a full PUT built from the form fields (the same shape submit() uses),
   * because these resources expose no per-field PATCH. Server-managed columns are
   * excluded by construction: formFields already drops `hideInForm` ones, so a
   * verification change can't accidentally overwrite a review score.
   */
  onRowEdit(editor: RowEditorConfig, row: Row, option: string): void {
    if (this.rowEditorValue(editor, row) === option) {
      return; // no change (the directive re-asserting the same value)
    }
    const payload: Record<string, unknown> = {};
    for (const f of this.formFields) {
      const raw = f.key === editor.key ? editor.toValue(option) : row[f.key];
      if (f.type === 'boolean') {
        payload[f.key] = !!raw;
        continue;
      }
      if (raw === null || raw === undefined || raw === '') {
        continue;
      }
      payload[f.key] = f.type === 'number' || f.type === 'money' || f.type === 'reference' ? Number(raw) : raw;
    }

    this.savingRows.update((s) => new Set(s).add(row.id));
    const release = () =>
      this.savingRows.update((s) => {
        const next = new Set(s);
        next.delete(row.id);
        return next;
      });

    this.crud.update<Row>(this.config.apiBase, row.id, payload).subscribe({
      next: (saved) => {
        release();
        // Patch the one row in place rather than reloading: a full reload would
        // reset the page and lose the reader's position in a long table.
        this.rows.update((rows) => rows.map((r) => (r.id === row.id ? { ...r, ...saved } : r)));
        this.toast.success(`${columnLabelOf(this.config, editor.key)} updated.`);
      },
      error: release,
    });
  }

  // ---------------- long-text viewer ----------------

  /** The cell whose full text is open in the reader modal, or null. */
  readonly textView = signal<{ title: string; body: string } | null>(null);

  /** True when the table will ellipsise this cell, so it needs a way out. */
  isLongText(row: Row, column: string): boolean {
    return this.display(row, column).length > TRUNCATE_AT;
  }

  openText(row: Row, column: string): void {
    this.textView.set({ title: this.columnLabel(column), body: this.display(row, column) });
  }

  closeText(): void {
    this.textView.set(null);
  }

  money(value: unknown): string {
    return formatRupees(value, String(value));
  }

  /** Bootstrap contextual colour for an enum value, by name heuristics. */
  badgeClass(value: unknown): string {
    const v = String(value).toUpperCase();
    if (['ACTIVE', 'CONFIRMED', 'PAID', 'DONE', 'COMPLETED', 'RESOLVED', 'CHECKED_IN', 'CHECKED_OUT', 'AVAILABLE', 'LISTED', 'PUBLISHED', 'TRUSTED', 'ID_VERIFIED'].includes(v)) {
      return 'text-bg-success';
    }
    if (['CANCELLED', 'FAILED', 'REMOVED', 'BLACKLISTED', 'OVERDUE', 'EMERGENCY', 'NO_SHOW', 'DAMAGE_REPORTED', 'ISSUE_REPORTED', 'SUSPENDED'].includes(v)) {
      return 'text-bg-danger';
    }
    if (['PENDING', 'DRAFT', 'UNREAD', 'SCHEDULED', 'OPEN', 'UNVERIFIED', 'UNLISTED'].includes(v)) {
      return 'text-bg-secondary';
    }
    if (['IN_PROGRESS', 'ASSIGNED', 'ISSUED', 'HIGH', 'UNDER_MAINTENANCE', 'MODERATED', 'BLOCKED'].includes(v)) {
      return 'text-bg-warning';
    }
    return 'text-bg-info';
  }

  goToPage(p: number): void {
    if (p >= 1 && p <= this.pageCount()) {
      this.page.set(p);
    }
  }

  onSearch(event: Event): void {
    this.search.set((event.target as HTMLInputElement).value);
    this.page.set(1);
  }
}

/** Column label lookup usable outside the component instance. */
function columnLabelOf(config: ResourceConfig, key: string): string {
  return config.fields.find((f) => f.key === key)?.label ?? key;
}
