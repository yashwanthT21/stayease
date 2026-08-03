import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { CrudService } from '../../core/services/crud.service';
import { ToastService } from '../../core/services/toast.service';
import { AuthService } from '../../core/auth/auth.service';
import { MaintenanceIssueResponse, PropertyResponse } from '../../core/models/dtos';
import { MAINTENANCE_CATEGORIES, MAINTENANCE_PRIORITIES, MAINTENANCE_STATUSES, REPORTED_BY_TYPES } from '../../core/models/enums';
import { LabelizePipe } from '../../shared/pipes/labelize.pipe';
import { OwnerPageHeaderComponent } from '../../shared/ui/owner-page-header';
import { OwnerDialogComponent } from '../../shared/ui/owner-dialog';

/**
 * Maintenance issues screen (Maintenance domain) — a bespoke, self-contained
 * CRUD screen for /api/maintenance-issues that mirrors the backend `maintenance`
 * module's MaintenanceIssue. It lists issues and creates / edits / deletes them
 * through a modal form. The property is chosen from a dropdown (loaded from
 * /api/properties) so an operator never types a raw id.
 *
 * A PROPERTY_MANAGER only sees issues for the properties assigned to them (the
 * backend scopes /api/properties, and we filter the list to those ids). Native
 * <select>s are driven by signals + (change) rather than formControlName so the
 * first choice registers reliably in this zoneless app.
 */
@Component({
  selector: 'app-maintenance-issue',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, LabelizePipe, OwnerPageHeaderComponent, OwnerDialogComponent],
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
    return this.fb.group({
      reportedById: [row?.reportedById ?? '', [Validators.required, Validators.min(1)]],
      description: [row?.description ?? ''],
      assignedContractorId: [row?.assignedContractorId ?? ''],
      resolvedDate: [row?.resolvedDate ? String(row.resolvedDate).slice(0, 16) : ''],
      amountSpent: [row?.amountSpent ?? ''],
    });
  }

  protected onSearch(event: Event): void {
    this.search.set((event.target as HTMLInputElement).value);
  }
  protected onPropertyFilter(event: Event): void {
    this.propertyFilter.set((event.target as HTMLSelectElement).value);
  }
  protected onStatusFilter(event: Event): void {
    this.statusFilter.set((event.target as HTMLSelectElement).value);
  }

  protected onProperty(event: Event): void {
    const raw = (event.target as HTMLSelectElement).value;
    this.selectedPropertyId.set(raw ? Number(raw) : null);
  }
  protected onReporterType(event: Event): void {
    this.selectedReporterType.set((event.target as HTMLSelectElement).value);
  }
  protected onCategory(event: Event): void {
    this.selectedCategory.set((event.target as HTMLSelectElement).value);
  }
  protected onPriority(event: Event): void {
    this.selectedPriority.set((event.target as HTMLSelectElement).value);
  }
  protected onStatus(event: Event): void {
    this.selectedStatus.set((event.target as HTMLSelectElement).value);
  }

  protected openCreate(): void {
    this.editingId.set(null);
    this.attempted.set(false);
    this.selectedPropertyId.set(null);
    this.selectedReporterType.set('');
    this.selectedCategory.set('');
    this.selectedPriority.set('MEDIUM');
    this.selectedStatus.set('OPEN');
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
    this.form = this.buildForm(row);
    this.modalOpen.set(true);
  }

  protected closeModal(): void {
    this.modalOpen.set(false);
  }

  protected propertyMissing(): boolean {
    return this.attempted() && this.selectedPropertyId() == null;
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
    if (this.form.invalid || propertyId == null || !this.selectedReporterType() || !this.selectedCategory()) {
      this.form.markAllAsTouched();
      return;
    }

    const raw = this.form.getRawValue() as Record<string, unknown>;
    const payload: Record<string, unknown> = {
      propertyId,
      reportedByType: this.selectedReporterType(),
      category: this.selectedCategory(),
      priority: this.selectedPriority(),
      status: this.selectedStatus(),
    };
    for (const [key, value] of Object.entries(raw)) {
      if (value === null || value === undefined || value === '') {
        continue; // omit empty optionals so the backend applies its defaults
      }
      payload[key] =
        key === 'reportedById' || key === 'assignedContractorId' || key === 'amountSpent' ? Number(value) : value;
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

  protected money(value: unknown): string {
    if (value == null || value === '') {
      return '—';
    }
    const n = Number(value);
    return Number.isFinite(n) ? '$' + n.toFixed(2) : '—';
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
