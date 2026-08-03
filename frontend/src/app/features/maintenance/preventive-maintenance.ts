import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { CrudService } from '../../core/services/crud.service';
import { ToastService } from '../../core/services/toast.service';
import { AuthService } from '../../core/auth/auth.service';
import { PreventiveMaintenanceResponse, PropertyResponse } from '../../core/models/dtos';
import { PREVENTIVE_FREQUENCIES, PREVENTIVE_STATUSES } from '../../core/models/enums';
import { LabelizePipe } from '../../shared/pipes/labelize.pipe';
import { OwnerPageHeaderComponent } from '../../shared/ui/owner-page-header';
import { OwnerDialogComponent } from '../../shared/ui/owner-dialog';

/**
 * Preventive maintenance screen (Maintenance domain) — a bespoke, self-contained
 * CRUD screen for /api/preventive-maintenance that mirrors the backend
 * `maintenance` module's PreventiveMaintenance. It lists scheduled upkeep tasks
 * and creates / edits / deletes them through a modal form; the property is
 * chosen from a dropdown loaded from /api/properties.
 *
 * A PROPERTY_MANAGER only sees tasks for the properties assigned to them.
 * Native <select>s are driven by signals + (change) rather than formControlName
 * so the first choice registers reliably in this zoneless app.
 */
@Component({
  selector: 'app-preventive-maintenance',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, LabelizePipe, OwnerPageHeaderComponent, OwnerDialogComponent],
  templateUrl: './preventive-maintenance.html',
})
export class PreventiveMaintenanceComponent {
  private fb = inject(FormBuilder);
  private crud = inject(CrudService);
  private toast = inject(ToastService);
  private auth = inject(AuthService);

  private readonly api = '/api/preventive-maintenance';

  protected readonly frequencies = PREVENTIVE_FREQUENCIES;
  protected readonly statuses = PREVENTIVE_STATUSES;

  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly rows = signal<PreventiveMaintenanceResponse[]>([]);
  protected readonly properties = signal<PropertyResponse[]>([]);
  protected readonly search = signal('');
  protected readonly propertyFilter = signal<string>('');

  // For a PROPERTY_MANAGER: the ids of the properties they manage.
  private readonly scopePropIds = signal<Set<number> | null>(null);

  protected readonly modalOpen = signal(false);
  protected readonly editingId = signal<number | null>(null);
  protected readonly deleteTarget = signal<PreventiveMaintenanceResponse | null>(null);
  protected readonly attempted = signal(false);

  // Native <select>s → signals.
  protected readonly selectedPropertyId = signal<number | null>(null);
  protected readonly selectedFrequency = signal<string>('MONTHLY');
  protected readonly selectedStatus = signal<string>('SCHEDULED');

  protected form: FormGroup = this.buildForm();

  protected readonly filtered = computed(() => {
    const scope = this.scopePropIds();
    let rows = scope ? this.rows().filter((r) => scope.has(r.propertyId)) : this.rows();
    const prop = this.propertyFilter();
    if (prop) {
      rows = rows.filter((r) => r.propertyId === Number(prop));
    }
    const term = this.search().trim().toLowerCase();
    if (!term) {
      return rows;
    }
    return rows.filter((r) =>
      `${this.propertyTitle(r.propertyId)} ${r.taskName} ${r.frequency} ${r.status}`.toLowerCase().includes(term),
    );
  });

  constructor() {
    this.load();
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
    this.crud.list<PreventiveMaintenanceResponse>(this.api).subscribe({
      next: (rows) => {
        this.rows.set(rows);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  private buildForm(row?: PreventiveMaintenanceResponse): FormGroup {
    return this.fb.group({
      taskName: [row?.taskName ?? '', [Validators.required, Validators.maxLength(150)]],
      nextScheduledDate: [row?.nextScheduledDate ? String(row.nextScheduledDate).slice(0, 10) : ''],
      lastCompletedDate: [row?.lastCompletedDate ? String(row.lastCompletedDate).slice(0, 10) : ''],
    });
  }

  protected onSearch(event: Event): void {
    this.search.set((event.target as HTMLInputElement).value);
  }
  protected onPropertyFilter(event: Event): void {
    this.propertyFilter.set((event.target as HTMLSelectElement).value);
  }

  protected onProperty(event: Event): void {
    const raw = (event.target as HTMLSelectElement).value;
    this.selectedPropertyId.set(raw ? Number(raw) : null);
  }
  protected onFrequency(event: Event): void {
    this.selectedFrequency.set((event.target as HTMLSelectElement).value);
  }
  protected onStatus(event: Event): void {
    this.selectedStatus.set((event.target as HTMLSelectElement).value);
  }

  protected openCreate(): void {
    this.editingId.set(null);
    this.attempted.set(false);
    this.selectedPropertyId.set(null);
    this.selectedFrequency.set('MONTHLY');
    this.selectedStatus.set('SCHEDULED');
    this.form = this.buildForm();
    this.modalOpen.set(true);
  }

  protected openEdit(row: PreventiveMaintenanceResponse): void {
    this.editingId.set(row.id);
    this.attempted.set(false);
    this.selectedPropertyId.set(row.propertyId ?? null);
    this.selectedFrequency.set(row.frequency);
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

  protected submit(): void {
    this.attempted.set(true);
    const propertyId = this.selectedPropertyId();
    if (this.form.invalid || propertyId == null) {
      this.form.markAllAsTouched();
      return;
    }

    const raw = this.form.getRawValue() as Record<string, unknown>;
    const payload: Record<string, unknown> = {
      propertyId,
      frequency: this.selectedFrequency(),
      status: this.selectedStatus(),
    };
    for (const [key, value] of Object.entries(raw)) {
      if (value === null || value === undefined || value === '') {
        continue; // omit empty optionals so the backend applies its defaults
      }
      payload[key] = value;
    }

    this.saving.set(true);
    const id = this.editingId();
    const req$ =
      id === null
        ? this.crud.create<PreventiveMaintenanceResponse>(this.api, payload)
        : this.crud.update<PreventiveMaintenanceResponse>(this.api, id, payload);

    req$.subscribe({
      next: (saved) => {
        this.saving.set(false);
        this.modalOpen.set(false);
        this.toast.success(`Preventive task ${id === null ? 'created' : 'updated'}.`);
        this.upsertRow(saved);
      },
      error: () => this.saving.set(false),
    });
  }

  /** Insert (create) or replace (edit) a row from the authoritative server response. */
  private upsertRow(row: PreventiveMaintenanceResponse): void {
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

  protected askDelete(row: PreventiveMaintenanceResponse): void {
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
        this.toast.success('Preventive task deleted.');
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

  protected statusBadge(status: string): string {
    switch (status) {
      case 'COMPLETED':
        return 'text-bg-success';
      case 'OVERDUE':
        return 'text-bg-danger';
      default:
        return 'text-bg-secondary';
    }
  }

  protected invalid(control: string): boolean {
    const c = this.form.get(control);
    return !!c && c.invalid && (c.dirty || c.touched);
  }
}
