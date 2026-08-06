import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { CrudService } from '../../core/services/crud.service';
import { ToastService } from '../../core/services/toast.service';
import { AuthService } from '../../core/auth/auth.service';
import { MaintenanceIssueResponse, PropertyResponse, UserResponse } from '../../core/models/dtos';
import { MAINTENANCE_CATEGORIES, MAINTENANCE_PRIORITIES, MAINTENANCE_STATUSES, REPORTED_BY_TYPES } from '../../core/models/enums';
import { LabelizePipe } from '../../shared/pipes/labelize.pipe';
import { formatRupees } from '../../shared/money';
import { OwnerPageHeaderComponent } from '../../shared/ui/owner-page-header';
import { OwnerDialogComponent } from '../../shared/ui/owner-dialog';
import { SelectValueDirective } from '../../shared/ui/select-value';

/**
 * Maintenance issues screen (Maintenance domain) — a bespoke, self-contained
 * CRUD screen for /api/maintenance-issues that mirrors the backend `maintenance`
 * module's MaintenanceIssue. It lists issues and creates / edits / deletes them
 * through a modal form. The property is chosen from a dropdown (loaded from
 * /api/properties) so an operator never types a raw id.
 *
 * A PROPERTY_MANAGER only sees issues for the properties assigned to them (the
 * backend scopes /api/properties, and we filter the list to those ids). Native
 * <select>s are driven by signals through SelectValueDirective rather than
 * formControlName, so a choice registers reliably in this zoneless app AND sticks
 * when the option list re-renders.
 */
@Component({
  selector: 'app-maintenance-issue',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, LabelizePipe, OwnerPageHeaderComponent, OwnerDialogComponent, SelectValueDirective],
  templateUrl: './maintenance-issue.html',
})
export class MaintenanceIssueComponent {
  private fb = inject(FormBuilder);
  private crud = inject(CrudService);
  private toast = inject(ToastService);
  private auth = inject(AuthService);

  private readonly api = '/api/maintenance-issues';

  protected readonly categories = MAINTENANCE_CATEGORIES;
  protected readonly priorities = MAINTENANCE_PRIORITIES;
  protected readonly statuses = MAINTENANCE_STATUSES;
  protected readonly reporterTypes = REPORTED_BY_TYPES;

  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly rows = signal<MaintenanceIssueResponse[]>([]);
  protected readonly properties = signal<PropertyResponse[]>([]);
  /** Feeds the "Reported by" people picker and resolves ids to names in the table. */
  protected readonly users = signal<UserResponse[]>([]);
  protected readonly search = signal('');
  protected readonly propertyFilter = signal<string>('');
  protected readonly statusFilter = signal<string>('');

  // For a PROPERTY_MANAGER: the ids of the properties they manage. Non-null only
  // for managers, so the list is limited to their own properties' issues.
  private readonly scopePropIds = signal<Set<number> | null>(null);

  protected readonly modalOpen = signal(false);
  protected readonly editingId = signal<number | null>(null);
  protected readonly deleteTarget = signal<MaintenanceIssueResponse | null>(null);
  protected readonly attempted = signal(false);

  // Native <select>s → signals (see class comment).
  protected readonly selectedPropertyId = signal<number | null>(null);
  protected readonly selectedReporterType = signal<string>('');
  protected readonly selectedCategory = signal<string>('');
  protected readonly selectedPriority = signal<string>('MEDIUM');
  protected readonly selectedStatus = signal<string>('OPEN');

  // ---- "Reported by" people picker ----
  // A type-to-search combobox over the user directory. The id is what gets sent;
  // the query string is only what the operator typed, so a half-typed name can
  // never be mistaken for a selection.
  protected readonly selectedReporterId = signal<number | null>(null);
  protected readonly reporterQuery = signal('');
  protected readonly reporterOpen = signal(false);

  protected readonly reporterMatches = computed(() => {
    const term = this.reporterQuery().trim().toLowerCase();
    const all = this.users();
    const matches = term
      ? all.filter((u) => `${u.name} ${u.email} ${u.role}`.toLowerCase().includes(term))
      : all;
    return matches.slice(0, 8); // keep the dropdown scannable
  });

  // Plain fields live in the reactive form; the selects are signals.
  protected form: FormGroup = this.buildForm();

  protected readonly filtered = computed(() => {
    const scope = this.scopePropIds();
    let rows = scope ? this.rows().filter((r) => scope.has(r.propertyId)) : this.rows();
    const prop = this.propertyFilter();
    if (prop) {
      rows = rows.filter((r) => r.propertyId === Number(prop));
    }
    const status = this.statusFilter();
    if (status) {
      rows = rows.filter((r) => r.status === status);
    }
    const term = this.search().trim().toLowerCase();
    if (!term) {
      return rows;
    }
    return rows.filter((r) =>
      `${this.propertyTitle(r.propertyId)} ${r.category} ${r.priority} ${r.reportedByType} ${r.status}`
        .toLowerCase()
        .includes(term),
    );
  });

  constructor() {
    this.load();
    // Properties feed the picker and resolve ids to titles. For a manager the
    // backend scopes this to their own properties → also our list scope.
    this.crud.list<PropertyResponse>('/api/properties').subscribe({
      next: (props) => {
        this.properties.set(props);
        if (this.auth.role() === 'PROPERTY_MANAGER') {
          this.scopePropIds.set(new Set(props.map((p) => p.id)));
        }
      },
      error: () => this.properties.set([]),
    });
    // The people picker's directory. A 403 (role without directory access) just
    // leaves the picker empty rather than breaking the screen.
    this.crud.list<UserResponse>('/api/users/directory').subscribe({
      next: (rows) => this.users.set(rows),
      error: () => this.users.set([]),
    });
  }

  private load(): void {
    this.loading.set(true);
    this.crud.list<MaintenanceIssueResponse>(this.api).subscribe({
      next: (rows) => {
        this.rows.set(rows);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  private buildForm(row?: MaintenanceIssueResponse): FormGroup {
    // reportedById is NOT a form control — it comes from the people picker signal.
    return this.fb.group({
      description: [row?.description ?? ''],
      assignedContractorId: [row?.assignedContractorId ?? ''],
      resolvedDate: [row?.resolvedDate ? String(row.resolvedDate).slice(0, 16) : ''],
      amountSpent: [row?.amountSpent ?? ''],
    });
  }

  protected onSearch(event: Event): void {
    this.search.set((event.target as HTMLInputElement).value);
  }
  // Every select on this screen is bound through SelectValueDirective, which
  // re-asserts the signal's value onto the element after each render. That's what
  // stops a pick reverting to the first option when the properties list resolves
  // after the modal opened, or when @for re-renders the option list.
  protected onPropertyFilter(value: string): void {
    this.propertyFilter.set(value);
  }
  protected onStatusFilter(value: string): void {
    this.statusFilter.set(value);
  }

  protected onProperty(value: string): void {
    this.selectedPropertyId.set(value ? Number(value) : null);
  }
  protected onReporterType(value: string): void {
    this.selectedReporterType.set(value);
  }
  protected onCategory(value: string): void {
    this.selectedCategory.set(value);
  }
  protected onPriority(value: string): void {
    this.selectedPriority.set(value);
  }
  protected onStatus(value: string): void {
    this.selectedStatus.set(value);
  }

  // ---- "Reported by" people picker ----
  protected onReporterSearch(event: Event): void {
    this.reporterQuery.set((event.target as HTMLInputElement).value);
    // Typing invalidates any earlier pick — the operator must choose again, so we
    // never save a stale id under a freshly typed name.
    this.selectedReporterId.set(null);
    this.reporterOpen.set(true);
  }

  protected openReporterList(): void {
    this.reporterOpen.set(true);
  }

  protected closeReporterList(): void {
    this.reporterOpen.set(false);
  }

  /** Bound to (mousedown) so it lands before the input's blur closes the list. */
  protected pickReporter(user: UserResponse): void {
    this.selectedReporterId.set(user.id);
    this.reporterQuery.set(user.name);
    this.reporterOpen.set(false);
  }

  protected clearReporter(): void {
    this.selectedReporterId.set(null);
    this.reporterQuery.set('');
    this.reporterOpen.set(false);
  }

  protected selectedReporter(): UserResponse | undefined {
    const id = this.selectedReporterId();
    return id == null ? undefined : this.users().find((u) => u.id === id);
  }

  protected openCreate(): void {
    this.editingId.set(null);
    this.attempted.set(false);
    this.selectedPropertyId.set(null);
    this.selectedReporterType.set('');
    this.selectedCategory.set('');
    this.selectedPriority.set('MEDIUM');
    this.selectedStatus.set('OPEN');
    this.clearReporter();
    this.form = this.buildForm();
    this.modalOpen.set(true);
  }

  protected openEdit(row: MaintenanceIssueResponse): void {
    this.editingId.set(row.id);
    this.attempted.set(false);
    this.selectedPropertyId.set(row.propertyId ?? null);
    this.selectedReporterType.set(row.reportedByType);
    this.selectedCategory.set(row.category);
    this.selectedPriority.set(row.priority);
    this.selectedStatus.set(row.status);
    this.selectedReporterId.set(row.reportedById ?? null);
    // Show the person's name when we know it; fall back to the raw id for a user
    // the directory doesn't cover (deleted account, or no directory access).
    this.reporterQuery.set(row.reportedById != null ? this.userName(row.reportedById) : '');
    this.reporterOpen.set(false);
    this.form = this.buildForm(row);
    this.modalOpen.set(true);
  }

  protected closeModal(): void {
    this.modalOpen.set(false);
  }

  protected propertyMissing(): boolean {
    return this.attempted() && this.selectedPropertyId() == null;
  }
  protected reportedByMissing(): boolean {
    return this.attempted() && this.selectedReporterId() == null;
  }
  protected reporterMissing(): boolean {
    return this.attempted() && !this.selectedReporterType();
  }
  protected categoryMissing(): boolean {
    return this.attempted() && !this.selectedCategory();
  }

  protected submit(): void {
    this.attempted.set(true);
    const propertyId = this.selectedPropertyId();
    const reportedById = this.selectedReporterId();
    if (
      this.form.invalid ||
      propertyId == null ||
      reportedById == null ||
      !this.selectedReporterType() ||
      !this.selectedCategory()
    ) {
      this.form.markAllAsTouched();
      return;
    }

    const raw = this.form.getRawValue() as Record<string, unknown>;
    const payload: Record<string, unknown> = {
      propertyId,
      reportedById,
      reportedByType: this.selectedReporterType(),
      category: this.selectedCategory(),
      priority: this.selectedPriority(),
      status: this.selectedStatus(),
    };
    for (const [key, value] of Object.entries(raw)) {
      if (value === null || value === undefined || value === '') {
        continue; // omit empty optionals so the backend applies its defaults
      }
      payload[key] = key === 'assignedContractorId' || key === 'amountSpent' ? Number(value) : value;
    }

    this.saving.set(true);
    const id = this.editingId();
    const req$ =
      id === null
        ? this.crud.create<MaintenanceIssueResponse>(this.api, payload)
        : this.crud.update<MaintenanceIssueResponse>(this.api, id, payload);

    req$.subscribe({
      next: (saved) => {
        this.saving.set(false);
        this.modalOpen.set(false);
        this.toast.success(`Issue ${id === null ? 'created' : 'updated'}.`);
        this.upsertRow(saved);
      },
      error: () => this.saving.set(false),
    });
  }

  /** Insert (create) or replace (edit) a row from the authoritative server response. */
  private upsertRow(row: MaintenanceIssueResponse): void {
    this.rows.update((rows) => {
      const idx = rows.findIndex((r) => r.id === row.id);
      if (idx === -1) {
        return [row, ...rows];
      }
      const copy = [...rows];
      copy[idx] = row;
      return copy;
    });
  }

  protected askDelete(row: MaintenanceIssueResponse): void {
    this.deleteTarget.set(row);
  }
  protected cancelDelete(): void {
    this.deleteTarget.set(null);
  }
  protected confirmDelete(): void {
    const row = this.deleteTarget();
    if (!row) {
      return;
    }
    this.crud.remove(this.api, row.id).subscribe({
      next: () => {
        this.rows.update((rows) => rows.filter((r) => r.id !== row.id));
        this.deleteTarget.set(null);
        this.toast.success('Issue deleted.');
      },
      error: () => this.deleteTarget.set(null),
    });
  }

  // ---- display helpers ----
  protected propertyTitle(id: number | undefined): string {
    if (id == null) {
      return '—';
    }
    const p = this.properties().find((x) => x.id === Number(id));
    return p ? p.title : `#${id}`;
  }

  protected userName(id: number | undefined): string {
    if (id == null) {
      return '—';
    }
    const u = this.users().find((x) => x.id === Number(id));
    return u ? u.name : `#${id}`;
  }

  protected money(value: unknown): string {
    return formatRupees(value);
  }

  protected fmtDateTime(value: string | undefined): string {
    return value ? String(value).replace('T', ' ').slice(0, 16) : '—';
  }

  protected statusBadge(status: string): string {
    switch (status) {
      case 'RESOLVED':
      case 'CLOSED':
        return 'text-bg-success';
      case 'IN_PROGRESS':
      case 'ASSIGNED':
        return 'text-bg-warning';
      default:
        return 'text-bg-secondary';
    }
  }

  protected priorityBadge(priority: string): string {
    switch (priority) {
      case 'EMERGENCY':
        return 'text-bg-danger';
      case 'HIGH':
        return 'text-bg-warning';
      case 'LOW':
        return 'text-bg-info';
      default:
        return 'text-bg-secondary';
    }
  }

  protected invalid(control: string): boolean {
    const c = this.form.get(control);
    return !!c && c.invalid && (c.dirty || c.touched);
  }
}
