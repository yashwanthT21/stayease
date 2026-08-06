import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { CrudService } from '../../core/services/crud.service';
import { ToastService } from '../../core/services/toast.service';
import { OwnerDataService } from '../../core/services/owner-data.service';
import { PropertyResponse, UserResponse } from '../../core/models/dtos';
import { PROPERTY_STATUSES, PROPERTY_TYPES } from '../../core/models/enums';
import { LabelizePipe } from '../../shared/pipes/labelize.pipe';
import { OwnerPageHeaderComponent } from '../../shared/ui/owner-page-header';
import { OwnerDialogComponent } from '../../shared/ui/owner-dialog';
import { SelectValueDirective } from '../../shared/ui/select-value';

/**
 * Standard Indian hotel timings: guests may check in from 2:00 PM and must check
 * out by 11:00 AM. Offered as a one-click default so an owner listing a property
 * doesn't have to key the usual values in every time.
 */
export const DEFAULT_CHECK_IN_TIME = '14:00';
export const DEFAULT_CHECK_OUT_TIME = '11:00';

/**
 * The owner's property CRUD screen. Identical contract to the generic resource
 * page, but hard-scoped to the signed-in owner: the list is filtered by
 * ownerId, and every create/update forces ownerId to the current user so an
 * owner can never author a listing under someone else's account.
 */
@Component({
  selector: 'app-owner-property-manager',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, LabelizePipe, OwnerPageHeaderComponent, OwnerDialogComponent, SelectValueDirective],
  templateUrl: './property-manager.html',
})
export class PropertyManagerComponent {
  private fb = inject(FormBuilder);
  private crud = inject(CrudService);
  private toast = inject(ToastService);
  private data = inject(OwnerDataService);

  protected readonly types = PROPERTY_TYPES;
  protected readonly statuses = PROPERTY_STATUSES;

  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly rows = signal<PropertyResponse[]>([]);
  protected readonly search = signal('');

  protected readonly modalOpen = signal(false);
  protected readonly editingId = signal<number | null>(null);
  protected readonly deleteTarget = signal<PropertyResponse | null>(null);

  // Managers are chosen from a dropdown (loaded from the DB) rather than typed.
  // Kept as a signal (not a reactive control) so the native <select> reflects
  // and captures the choice reliably on the first interaction in this zoneless app.
  protected readonly managers = signal<UserResponse[]>([]);
  protected readonly selectedManagerId = signal<number | null>(null);

  // Type and status are native <select>s too. For the same zoneless reliability
  // reason as the manager picker, they're driven by signals + (change) rather
  // than formControlName — otherwise the first choice may not register and the
  // form keeps the default (e.g. a "Listed" pick saving as UNLISTED).
  protected readonly selectedType = signal<string>('APARTMENT');
  protected readonly selectedStatus = signal<string>('UNLISTED');

  /**
   * Check-in / check-out times are either the standard Indian hotel timings
   * (2:00 PM → 11:00 AM) or picked by hand. Only drives which choice the UI
   * highlights — the values themselves always live in the form controls, so the
   * payload is built the same way either way.
   */
  protected readonly stayTimeMode = signal<'DEFAULT' | 'CUSTOM'>('DEFAULT');
  protected readonly defaultCheckInTime = DEFAULT_CHECK_IN_TIME;
  protected readonly defaultCheckOutTime = DEFAULT_CHECK_OUT_TIME;

  protected form: FormGroup = this.buildForm();

  protected readonly filtered = computed(() => {
    const term = this.search().trim().toLowerCase();
    const rows = this.rows();
    if (!term) {
      return rows;
    }
    return rows.filter((r) => `${r.title} ${r.city} ${r.type} ${r.status}`.toLowerCase().includes(term));
  });

  constructor() {
    this.load();
    // Populate the manager picker. A failure (e.g. 403) just leaves it empty.
    this.data.managers().subscribe({
      next: (rows) => this.managers.set(rows),
      error: () => this.managers.set([]),
    });
  }

  private load(): void {
    this.loading.set(true);
    this.data.myProperties().subscribe({
      next: (rows) => {
        this.rows.set(rows);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  private buildForm(row?: PropertyResponse): FormGroup {
    // type & status are managed as signals (see selectedType/selectedStatus).
    return this.fb.group({
      title: [row?.title ?? '', [Validators.required, Validators.maxLength(200)]],
      city: [row?.city ?? '', [Validators.required, Validators.maxLength(120)]],
      maxGuests: [row?.maxGuests ?? 1, [Validators.required, Validators.min(1)]],
      bedrooms: [row?.bedrooms ?? 0, [Validators.required, Validators.min(0)]],
      bathrooms: [row?.bathrooms ?? 0, [Validators.required, Validators.min(0)]],
      amenitiesList: [row?.amenitiesList ?? ''],
      houseRules: [row?.houseRules ?? ''],
      checkInTime: [row?.checkInTime ? String(row.checkInTime).slice(0, 5) : ''],
      checkOutTime: [row?.checkOutTime ? String(row.checkOutTime).slice(0, 5) : ''],
    });
  }

  protected onSearch(event: Event): void {
    this.search.set((event.target as HTMLInputElement).value);
  }

  protected openCreate(): void {
    this.editingId.set(null);
    this.selectedManagerId.set(null);
    this.selectedType.set('APARTMENT');
    this.selectedStatus.set('UNLISTED');
    this.form = this.buildForm();
    // A brand-new listing starts on the standard timings; the owner can switch
    // to "Set manually" (or just edit either time) to override them.
    this.useDefaultStayTimes();
    this.modalOpen.set(true);
  }

  protected openEdit(row: PropertyResponse): void {
    this.editingId.set(row.id);
    this.selectedManagerId.set(row.managerId ?? null);
    this.selectedType.set(row.type);
    this.selectedStatus.set(row.status);
    this.form = this.buildForm(row);
    this.stayTimeMode.set(this.matchesDefaultStayTimes() ? 'DEFAULT' : 'CUSTOM');
    this.modalOpen.set(true);
  }

  // ---- check-in / check-out timings ----

  /** One-click standard Indian hotel timings: check in 2:00 PM, check out 11:00 AM. */
  protected useDefaultStayTimes(): void {
    this.form.patchValue({
      checkInTime: DEFAULT_CHECK_IN_TIME,
      checkOutTime: DEFAULT_CHECK_OUT_TIME,
    });
    this.stayTimeMode.set('DEFAULT');
  }

  /** Hand the times back to the owner (values are left as-is to edit from). */
  protected useCustomStayTimes(): void {
    this.stayTimeMode.set('CUSTOM');
  }

  /** Editing either time directly counts as choosing custom timings. */
  protected onStayTimeEdited(): void {
    this.stayTimeMode.set(this.matchesDefaultStayTimes() ? 'DEFAULT' : 'CUSTOM');
  }

  private matchesDefaultStayTimes(): boolean {
    return (
      this.form.get('checkInTime')?.value === DEFAULT_CHECK_IN_TIME &&
      this.form.get('checkOutTime')?.value === DEFAULT_CHECK_OUT_TIME
    );
  }

  // These three selects are driven through SelectValueDirective, which re-asserts
  // the signal's value onto the element after every render — so a choice survives
  // the managers list resolving after the modal opened, and no longer silently
  // reverts to the first option while the signal still holds the real pick.
  protected onManagerChange(value: string): void {
    this.selectedManagerId.set(value ? Number(value) : null);
  }

  protected onTypeChange(value: string): void {
    this.selectedType.set(value);
  }

  protected onStatusChange(value: string): void {
    this.selectedStatus.set(value);
  }

  protected closeModal(): void {
    this.modalOpen.set(false);
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue() as Record<string, unknown>;
    const payload: Record<string, unknown> = { ownerId: this.data.ownerId() };
    for (const [key, value] of Object.entries(raw)) {
      if (value === null || value === undefined || value === '') {
        continue; // omit empty optionals so the backend applies defaults
      }
      if (key === 'maxGuests' || key === 'bedrooms' || key === 'bathrooms') {
        payload[key] = Number(value);
      } else {
        payload[key] = value;
      }
    }
    // type & status come from their signals (native selects — see above).
    payload['type'] = this.selectedType();
    payload['status'] = this.selectedStatus();
    // Assigned manager comes from the dropdown; omit when "no manager" is chosen.
    if (this.selectedManagerId() != null) {
      payload['managerId'] = this.selectedManagerId();
    }

    this.saving.set(true);
    const id = this.editingId();
    const req$ =
      id === null
        ? this.crud.create<PropertyResponse>('/api/properties', payload)
        : this.crud.update<PropertyResponse>('/api/properties', id, payload);

    req$.subscribe({
      next: (saved) => {
        this.saving.set(false);
        this.modalOpen.set(false);
        this.toast.success(`Property ${id === null ? 'created' : 'updated'}.`);
        // Render exactly what the server persisted (its returned row is the
        // source of truth for status et al.), rather than trusting the form
        // values or racing a fresh reload.
        this.upsertRow(saved);
      },
      error: () => this.saving.set(false),
    });
  }

  /** Insert (create) or replace (edit) a row from the authoritative server response. */
  private upsertRow(row: PropertyResponse): void {
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

  protected askDelete(row: PropertyResponse): void {
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
    this.crud.remove('/api/properties', row.id).subscribe({
      next: () => {
        this.rows.update((rows) => rows.filter((r) => r.id !== row.id));
        this.deleteTarget.set(null);
        this.toast.success('Property deleted.');
      },
      error: () => this.deleteTarget.set(null),
    });
  }

  protected statusBadge(status: string): string {
    switch (status) {
      case 'LISTED':
        return 'text-bg-success';
      case 'UNDER_MAINTENANCE':
        return 'text-bg-warning';
      default:
        return 'text-bg-secondary';
    }
  }

  protected invalid(control: string): boolean {
    const c = this.form.get(control);
    return !!c && c.invalid && (c.dirty || c.touched);
  }
}
