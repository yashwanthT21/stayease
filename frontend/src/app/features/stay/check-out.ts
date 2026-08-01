import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { CrudService } from '../../core/services/crud.service';
import { ToastService } from '../../core/services/toast.service';
import { CheckOutRecordResponse, ReservationResponse } from '../../core/models/dtos';
import { CHECK_OUT_STATUSES } from '../../core/models/enums';
import { LabelizePipe } from '../../shared/pipes/labelize.pipe';
import { OwnerPageHeaderComponent } from '../../shared/ui/owner-page-header';
import { OwnerDialogComponent } from '../../shared/ui/owner-dialog';

/**
 * Check-out screen (Stay domain) — a bespoke, self-contained CRUD screen for
 * /api/check-outs that mirrors the backend `stay` module's CheckOutRecord. It
 * lists every check-out record and creates / edits / deletes them through a
 * modal form, with the reservation chosen from a loaded dropdown.
 *
 * The generic engine could render this from `check-out.resource.ts`; it is
 * hand-written here so the Stay module owns an explicit, walk-through component
 * per screen — the same shape as `review-analytics`.
 */
@Component({
  selector: 'app-check-out',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, LabelizePipe, OwnerPageHeaderComponent, OwnerDialogComponent],
  templateUrl: './check-out.html',
})
export class CheckOutComponent {
  private fb = inject(FormBuilder);
  private crud = inject(CrudService);
  private toast = inject(ToastService);

  private readonly api = '/api/check-outs';

  protected readonly statuses = CHECK_OUT_STATUSES;

  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly rows = signal<CheckOutRecordResponse[]>([]);
  protected readonly search = signal('');

  // Reference list — populates the reservation picker and resolves the id to a
  // readable label in the table.
  protected readonly reservations = signal<ReservationResponse[]>([]);

  protected readonly modalOpen = signal(false);
  protected readonly editingId = signal<number | null>(null);
  protected readonly deleteTarget = signal<CheckOutRecordResponse | null>(null);

  // Reservation is a required reference — kept as a signal (see CheckInComponent
  // for why the async picker isn't a reactive control in this zoneless app).
  protected readonly selectedReservationId = signal<number | null>(null);
  protected readonly attempted = signal(false);

  // Status is a native <select> too — signal + (change) rather than
  // formControlName so the first choice registers reliably in this zoneless app.
  protected readonly selectedStatus = signal<string>('CHECKED_OUT');

  protected form: FormGroup = this.buildForm();

  protected readonly filtered = computed(() => {
    const term = this.search().trim().toLowerCase();
    const rows = this.rows();
    if (!term) {
      return rows;
    }
    return rows.filter((r) => `${this.reservationLabel(r.reservationId)} ${r.status}`.toLowerCase().includes(term));
  });

  constructor() {
    this.load();
    this.crud.list<ReservationResponse>('/api/reservations').subscribe({
      next: (rows) => this.reservations.set(rows),
      error: () => this.reservations.set([]),
    });
  }

  private load(): void {
    this.loading.set(true);
    this.crud.list<CheckOutRecordResponse>(this.api).subscribe({
      next: (rows) => {
        this.rows.set(rows);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  // Only the plain fields live in the reactive form; status is a signal.
  private buildForm(row?: CheckOutRecordResponse): FormGroup {
    return this.fb.group({
      actualCheckOut: [row?.actualCheckOut ? String(row.actualCheckOut).slice(0, 16) : ''],
      damageNoted: [row?.damageNoted ?? false],
      damageDescription: [row?.damageDescription ?? ''],
      depositReleased: [row?.depositReleased ?? false],
    });
  }

  protected onSearch(event: Event): void {
    this.search.set((event.target as HTMLInputElement).value);
  }

  protected onReservationChange(event: Event): void {
    const raw = (event.target as HTMLSelectElement).value;
    this.selectedReservationId.set(raw ? Number(raw) : null);
  }

  protected onStatusChange(event: Event): void {
    this.selectedStatus.set((event.target as HTMLSelectElement).value);
  }

  protected openCreate(): void {
    this.editingId.set(null);
    this.attempted.set(false);
    this.selectedReservationId.set(null);
    this.selectedStatus.set('CHECKED_OUT');
    this.form = this.buildForm();
    this.modalOpen.set(true);
  }

  protected openEdit(row: CheckOutRecordResponse): void {
    this.editingId.set(row.id);
    this.attempted.set(false);
    this.selectedReservationId.set(row.reservationId ?? null);
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

  protected submit(): void {
    this.attempted.set(true);
    const reservationId = this.selectedReservationId();
    if (this.form.invalid || reservationId == null) {
      this.form.markAllAsTouched();
      return;
    }

    const raw = this.form.getRawValue() as Record<string, unknown>;
    const payload: Record<string, unknown> = { reservationId };
    for (const [key, value] of Object.entries(raw)) {
      if (key === 'damageNoted' || key === 'depositReleased') {
        payload[key] = !!value;
        continue;
      }
      if (value === null || value === undefined || value === '') {
        continue; // omit empty optionals so the backend applies its defaults
      }
      payload[key] = value;
    }
    payload['status'] = this.selectedStatus(); // signal-backed select

    this.saving.set(true);
    const id = this.editingId();
    const req$ =
      id === null
        ? this.crud.create<CheckOutRecordResponse>(this.api, payload)
        : this.crud.update<CheckOutRecordResponse>(this.api, id, payload);

    req$.subscribe({
      next: (saved) => {
        this.saving.set(false);
        this.modalOpen.set(false);
        this.toast.success(`Check-out ${id === null ? 'created' : 'updated'}.`);
        this.upsertRow(saved);
      },
      error: () => this.saving.set(false),
    });
  }

  /** Insert (create) or replace (edit) a row from the authoritative server response. */
  private upsertRow(row: CheckOutRecordResponse): void {
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

  protected askDelete(row: CheckOutRecordResponse): void {
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
        this.toast.success('Check-out deleted.');
      },
      error: () => this.deleteTarget.set(null),
    });
  }

  // Resolve the reservation id to the same "#id · label" shape the generic engine uses.
  protected reservationLabel(id: number | undefined): string {
    if (id == null) {
      return '—';
    }
    const r = this.reservations().find((x) => x.id === Number(id));
    return r ? `#${r.id} · ${r.checkInDate} · ${r.checkOutDate}` : `#${id}`;
  }

  protected statusBadge(status: string): string {
    switch (status) {
      case 'CHECKED_OUT':
        return 'text-bg-success';
      case 'DAMAGE_REPORTED':
        return 'text-bg-danger';
      default:
        return 'text-bg-secondary';
    }
  }

  protected fmtDateTime(value: string | undefined): string {
    return value ? String(value).replace('T', ' ').slice(0, 16) : '—';
  }
}
