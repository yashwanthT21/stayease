import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { LowerCasePipe } from '@angular/common';
import { forkJoin, of, map, switchMap, catchError, Observable } from 'rxjs';
import { FormBuilder, FormGroup, ReactiveFormsModule, ValidatorFn, Validators } from '@angular/forms';
import { CrudService } from '../../core/services/crud.service';
import { ToastService } from '../../core/services/toast.service';
import { AuthService } from '../../core/auth/auth.service';
import { getResource } from '../../core/registry';
import { FieldConfig, FilterConfig, PatchAction, ResourceConfig } from '../crud/resource-config';
import { HasId } from '../../core/models/dtos';
import { LabelizePipe } from '../pipes/labelize.pipe';

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
  imports: [ReactiveFormsModule, LabelizePipe, LowerCasePipe],
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

  readonly filteredRows = computed(() => {
    const term = this.search().trim().toLowerCase();
    const rows = this.rows();
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
   * Write a native <select>'s chosen value into its reactive control. The
   * selects are bound this way — (change) + [selected] rather than
   * formControlName — because a formControlName <select> can drop the first
   * choice in this zoneless app (options are also async for references),
   * leaving required fields looking unselected. The control still owns
   * validation and submission; we only feed it the value reliably.
   */
  writeSelect(scope: 'form' | 'filter', key: string, event: Event): void {
    const group = scope === 'filter' ? this.filterForm : this.form;
    group?.get(key)?.setValue((event.target as HTMLSelectElement).value);
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
    return String(value);
  }

  money(value: unknown): string {
    const n = Number(value);
    return Number.isFinite(n) ? '$' + n.toFixed(2) : String(value);
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
