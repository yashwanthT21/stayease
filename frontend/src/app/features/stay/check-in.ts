import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { CrudService } from '../../core/services/crud.service';
import { ToastService } from '../../core/services/toast.service';
import { CheckInRecordResponse, GuestProfileResponse, ReservationResponse } from '../../core/models/dtos';
import { ACCESS_METHODS, CHECK_IN_STATUSES } from '../../core/models/enums';
import { LabelizePipe } from '../../shared/pipes/labelize.pipe';
import { OwnerPageHeaderComponent } from '../../shared/ui/owner-page-header';
import { OwnerDialogComponent } from '../../shared/ui/owner-dialog';

/**
 * Check-in screen (Stay domain) — a bespoke, self-contained CRUD screen for
 * /api/check-ins that mirrors the backend `stay` module's CheckInRecord. It
 * lists every check-in record and creates / edits / deletes them through a
 * modal form; the reservation and guest are chosen from dropdowns loaded from
 * their own endpoints so an operator never types a raw id.
 *
 * The generic engine could render this from `check-in.resource.ts`; it is
 * hand-written here so the Stay module owns an explicit, walk-through component
 * per screen — the same shape as `review-analytics`.
 */
@Component({
  selector: 'app-check-in',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, LabelizePipe, OwnerPageHeaderComponent, OwnerDialogComponent],
  templateUrl: './check-in.html',
})
export class CheckInComponent {
  private fb = inject(FormBuilder);
  private crud = inject(CrudService);
  private toast = inject(ToastService);

  private readonly api = '/api/check-ins';

  protected readonly accessMethods = ACCESS_METHODS;
  protected readonly statuses = CHECK_IN_STATUSES;

  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly rows = signal<CheckInRecordResponse[]>([]);
  protected readonly search = signal('');

  // Reference lists — populate the reservation / guest pickers and resolve ids
  // to readable labels in the table.
  protected readonly reservations = signal<ReservationResponse[]>([]);
  protected readonly guests = signal<GuestProfileResponse[]>([]);

  protected readonly modalOpen = signal(false);
  protected readonly editingId = signal<number | null>(null);
  protected readonly deleteTarget = signal<CheckInRecordResponse | null>(null);

  // Reservation / guest are required references. Like the property manager's
  // manager picker, they are kept as signals (not reactive controls) so the
  // async-populated <select> captures the first choice reliably in this zoneless
  // app. `attempted` reveals their validation message only after a submit try.
  protected readonly selectedReservationId = signal<number | null>(null);
  protected readonly selectedGuestId = signal<number | null>(null);
  protected readonly attempted = signal(false);

  // Access method & status are native <select>s too. Same zoneless reason as the
  // reference pickers above: driven by signals + (change), not formControlName,
  // so the first choice registers reliably instead of keeping the default.
  protected readonly selectedAccessMethod = signal<string>('');
  protected readonly selectedStatus = signal<string>('PENDING');

  protected form: FormGroup = this.buildForm();

  protected readonly filtered = computed(() => {
    const term = this.search().trim().toLowerCase();
    const rows = this.rows();
    if (!term) {
      return rows;
    }
    return rows.filter((r) =>
      `${this.reservationLabel(r.reservationId)} ${this.guestLabel(r.guestId)} ${r.status} ${r.accessMethod ?? ''}`
        .toLowerCase()
        .includes(term),
    );
  });

  constructor() {
    this.load();
    // Populate the reference pickers. A failure (e.g. 403) just leaves them
    // empty and ids fall back to a plain "#id".
    this.crud.list<ReservationResponse>('/api/reservations').subscribe({
      next: (rows) => this.reservations.set(rows),
      error: () => this.reservations.set([]),
    });
    this.crud.list<GuestProfileResponse>('/api/guests').subscribe({
      next: (rows) => this.guests.set(rows),
      error: () => this.guests.set([]),
    });
  }

  private load(): void {
    this.loading.set(true);
    this.crud.list<CheckInRecordResponse>(this.api).subscribe({
      next: (rows) => {
        this.rows.set(rows);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  // Only the plain fields live in the reactive form; the selects are signals.
  private buildForm(row?: CheckInRecordResponse): FormGroup {
    return this.fb.group({
      actualCheckIn: [row?.actualCheckIn ? String(row.actualCheckIn).slice(0, 16) : ''],
      welcomePackSent: [row?.welcomePackSent ?? false],
    });
  }

  protected onSearch(event: Event): void {
    this.search.set((event.target as HTMLInputElement).value);
  }

  protected onReservationChange(event: Event): void {
    const raw = (event.target as HTMLSelectElement).value;
    this.selectedReservationId.set(raw ? Number(raw) : null);
  }

  protected onGuestChange(event: Event): void {
    const raw = (event.target as HTMLSelectElement).value;
    this.selectedGuestId.set(raw ? Number(raw) : null);
  }

  protected onAccessMethodChange(event: Event): void {
    this.selectedAccessMethod.set((event.target as HTMLSelectElement).value);
  }

  protected onStatusChange(event: Event): void {
    this.selectedStatus.set((event.target as HTMLSelectElement).value);
  }

  protected openCreate(): void {
    this.editingId.set(null);
    this.attempted.set(false);
    this.selectedReservationId.set(null);
    this.selectedGuestId.set(null);
    this.selectedAccessMethod.set('');
    this.selectedStatus.set('PENDING');
    this.form = this.buildForm();
    this.modalOpen.set(true);
  }

  protected openEdit(row: CheckInRecordResponse): void {
    this.editingId.set(row.id);
    this.attempted.set(false);
    this.selectedReservationId.set(row.reservationId ?? null);
    this.selectedGuestId.set(row.guestId ?? null);
    this.selectedAccessMethod.set(row.accessMethod ?? '');
    this.selectedStatus.set(row.status);
    this.form = this.buildForm(row);
    this.modalOpen.set(true);
  }

  protected closeModal(): void {
    this.modalOpen.set(false);
  }

  protected reservationMissing(): boolean {
    return this.attempted() && this.selectedReservationId() == null;
  }

  protected guestMissing(): boolean {
    return this.attempted() && this.selectedGuestId() == null;
  }

  protected submit(): void {
    this.attempted.set(true);
    const reservationId = this.selectedReservationId();
    const guestId = this.selectedGuestId();
    if (this.form.invalid || reservationId == null || guestId == null) {
      this.form.markAllAsTouched();
      return;
    }

    const raw = this.form.getRawValue() as Record<string, unknown>;
    const payload: Record<string, unknown> = { reservationId, guestId };
    for (const [key, value] of Object.entries(raw)) {
      if (key === 'welcomePackSent') {
        payload[key] = !!value;
        continue;
      }
      if (value === null || value === undefined || value === '') {
        continue; // omit empty optionals so the backend applies its defaults
      }
      payload[key] = value;
    }
    // Selects (signal-backed): omit access method when "None"; status has a default.
    if (this.selectedAccessMethod()) {
      payload['accessMethod'] = this.selectedAccessMethod();
    }
    payload['status'] = this.selectedStatus();

    this.saving.set(true);
    const id = this.editingId();
    const req$ =
      id === null
        ? this.crud.create<CheckInRecordResponse>(this.api, payload)
        : this.crud.update<CheckInRecordResponse>(this.api, id, payload);

    req$.subscribe({
      next: (saved) => {
        this.saving.set(false);
        this.modalOpen.set(false);
        this.toast.success(`Check-in ${id === null ? 'created' : 'updated'}.`);
        this.upsertRow(saved);
      },
      error: () => this.saving.set(false),
    });
  }

  /** Insert (create) or replace (edit) a row from the authoritative server response. */
  private upsertRow(row: CheckInRecordResponse): void {
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

  protected askDelete(row: CheckInRecordResponse): void {
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
        this.toast.success('Check-in deleted.');
      },
      error: () => this.deleteTarget.set(null),
    });
  }

  // Resolve reference ids to the same "#id · label" shape the generic engine uses.
  protected reservationLabel(id: number | undefined): string {
    if (id == null) {
      return '—';
    }
    const r = this.reservations().find((x) => x.id === Number(id));
    return r ? `#${r.id} · ${r.checkInDate} · ${r.checkOutDate}` : `#${id}`;
  }

  protected guestLabel(id: number | undefined): string {
    if (id == null) {
      return '—';
    }
    const g = this.guests().find((x) => x.id === Number(id));
    return g ? `#${g.id} · ${g.name}` : `#${id}`;
  }

  protected statusBadge(status: string): string {
    switch (status) {
      case 'CHECKED_IN':
        return 'text-bg-success';
      case 'NO_SHOW':
        return 'text-bg-danger';
      default:
        return 'text-bg-secondary';
    }
  }

  protected fmtDateTime(value: string | undefined): string {
    return value ? String(value).replace('T', ' ').slice(0, 16) : '—';
  }
}
