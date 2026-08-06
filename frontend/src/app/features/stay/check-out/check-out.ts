import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { forkJoin, of, catchError } from 'rxjs';
import { CrudService } from '../../../core/services/crud.service';
import { ToastService } from '../../../core/services/toast.service';
import { AuthService } from '../../../core/auth/auth.service';
import { CheckOutRecordResponse, GuestProfileResponse, PropertyResponse, ReservationResponse } from '../../../core/models/dtos';
import { CHECK_OUT_STATUS_OPTIONS } from '../../../core/models/enums';
import { LabelizePipe } from '../../../shared/pipes/labelize.pipe';
import { OwnerPageHeaderComponent } from '../../../shared/ui/owner-page-header';
import { OwnerDialogComponent } from '../../../shared/ui/owner-dialog';
import { SelectValueDirective } from '../../../shared/ui/select-value';

/**
 * Check-out screen (Stay domain) — a bespoke, self-contained CRUD screen for
 * /api/check-outs that mirrors the backend `stay` module's CheckOutRecord. It
 * lists every check-out record and creates / edits / deletes them through a
 * modal form, with the reservation chosen from a loaded dropdown.
 *
 * Deposit release is deliberately not surfaced here: releasing a deposit is a
 * finance action, not part of recording a departure, so the field is left to the
 * backend's default rather than offered as a checkbox an operator must guess at.
 *
 * The generic engine could render this from `check-out.resource.ts`; it is
 * hand-written here so the Stay module owns an explicit, walk-through component
 * per screen — the same shape as `review-analytics`.
 */
@Component({
  selector: 'app-check-out',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, DatePipe, LabelizePipe, OwnerPageHeaderComponent, OwnerDialogComponent, SelectValueDirective],
  templateUrl: './check-out.html',
})
export class CheckOutComponent {
  private fb = inject(FormBuilder);
  private crud = inject(CrudService);
  private toast = inject(ToastService);
  private auth = inject(AuthService);

  private readonly api = '/api/check-outs';

  // For a PROPERTY_MANAGER: reservation ids at their own properties. Records and
  // the reservation picker are limited to these (null = no scoping).
  private readonly scopeResIds = signal<Set<number> | null>(null);

  // Only CHECKED_OUT is offered — damage is recorded by the "Damage noted" flag
  // below, not by a competing status value. See CHECK_OUT_STATUS_OPTIONS.
  protected readonly statuses = CHECK_OUT_STATUS_OPTIONS;

  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly rows = signal<CheckOutRecordResponse[]>([]);
  protected readonly search = signal('');

  // Reference list — populates the reservation picker and resolves the id to a
  // readable label in the table.
  protected readonly reservations = signal<ReservationResponse[]>([]);
  // Guest & property names enrich the reservation picker (who + where + when).
  protected readonly guests = signal<GuestProfileResponse[]>([]);
  protected readonly properties = signal<PropertyResponse[]>([]);

  protected readonly modalOpen = signal(false);
  protected readonly editingId = signal<number | null>(null);
  protected readonly deleteTarget = signal<CheckOutRecordResponse | null>(null);

  // Reservation and status are signal-backed selects bound through
  // SelectValueDirective (see CheckInComponent) — that's what keeps a chosen
  // value from reverting to the default once the reservation list resolves.
  protected readonly selectedReservationId = signal<number | null>(null);
  protected readonly attempted = signal(false);
  protected readonly selectedStatus = signal<string>('CHECKED_OUT');

  /**
   * Deposit release isn't editable on this screen (it's a finance concern), but
   * a PUT replaces the whole record — so we carry the stored value through an
   * edit instead of letting it silently reset to false.
   */
  private readonly depositReleased = signal(false);

  protected form: FormGroup = this.buildForm();

  protected readonly filtered = computed(() => {
    const scope = this.scopeResIds();
    const rows = scope ? this.rows().filter((r) => scope.has(r.reservationId)) : this.rows();
    const term = this.search().trim().toLowerCase();
    if (!term) {
      return rows;
    }
    return rows.filter((r) => `${this.reservationLabel(r.reservationId)} ${r.status}`.toLowerCase().includes(term));
  });

  constructor() {
    this.load();
    const reservations$ = this.crud.list<ReservationResponse>('/api/reservations').pipe(catchError(() => of([] as ReservationResponse[])));
    if (this.auth.role() === 'PROPERTY_MANAGER') {
      // Manager: limit reservations (and thus records) to their own properties.
      forkJoin({
        props: this.crud.list<PropertyResponse>('/api/properties').pipe(catchError(() => of([] as PropertyResponse[]))),
        res: reservations$,
      }).subscribe(({ props, res }) => {
        const propIds = new Set(props.map((p) => p.id));
        const mine = res.filter((r) => propIds.has(r.propertyId));
        this.reservations.set(mine);
        this.properties.set(props); // titles for their own properties
        this.scopeResIds.set(new Set(mine.map((r) => r.id)));
      });
    } else {
      reservations$.subscribe((rows) => {
        this.reservations.set(rows);
        this.loadPropertyTitles(rows);
      });
    }
    this.crud.list<GuestProfileResponse>('/api/guests').subscribe({
      next: (rows) => this.guests.set(rows),
      error: () => this.guests.set([]),
    });
  }

  /**
   * Resolve property titles by id (GET /api/properties/{id}) rather than the
   * list endpoint, which is scoped for a PROPERTY_MANAGER to their own assigned
   * properties — otherwise a manager sees "#id" for other properties. Fetching
   * by id is unscoped and works for every role.
   */
  private loadPropertyTitles(reservations: ReservationResponse[]): void {
    const ids = [...new Set(reservations.map((r) => r.propertyId).filter((id): id is number => id != null))];
    if (!ids.length) {
      this.properties.set([]);
      return;
    }
    forkJoin(
      ids.map((id) => this.crud.get<PropertyResponse>('/api/properties', id).pipe(catchError(() => of(null)))),
    ).subscribe((props) => this.properties.set(props.filter((p): p is PropertyResponse => !!p)));
  }

  protected reservation(id: number | undefined): ReservationResponse | undefined {
    return id == null ? undefined : this.reservations().find((x) => x.id === Number(id));
  }

  protected guestName(id: number | undefined): string {
    if (id == null) {
      return '—';
    }
    const g = this.guests().find((x) => x.id === Number(id));
    return g ? g.name : `#${id}`;
  }

  protected propertyTitle(id: number | undefined): string {
    if (id == null) {
      return '—';
    }
    const p = this.properties().find((x) => x.id === Number(id));
    return p ? p.title : `#${id}`;
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
    });
  }

  protected onSearch(event: Event): void {
    this.search.set((event.target as HTMLInputElement).value);
  }

  protected onReservationChange(value: string): void {
    this.selectedReservationId.set(value ? Number(value) : null);
  }

  protected onStatusChange(value: string): void {
    this.selectedStatus.set(value);
  }

  protected openCreate(): void {
    this.editingId.set(null);
    this.attempted.set(false);
    this.selectedReservationId.set(null);
    this.selectedStatus.set('CHECKED_OUT');
    this.depositReleased.set(false);
    this.form = this.buildForm();
    this.modalOpen.set(true);
  }

  protected openEdit(row: CheckOutRecordResponse): void {
    this.editingId.set(row.id);
    this.attempted.set(false);
    this.selectedReservationId.set(row.reservationId ?? null);
    this.selectedStatus.set(row.status);
    this.depositReleased.set(!!row.depositReleased);
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
      if (key === 'damageNoted') {
        payload[key] = !!value;
        continue;
      }
      if (value === null || value === undefined || value === '') {
        continue; // omit empty optionals so the backend applies its defaults
      }
      payload[key] = value;
    }
    payload['status'] = this.selectedStatus(); // signal-backed select
    // Not editable here — carried through so a PUT can't reset it (see above).
    payload['depositReleased'] = this.depositReleased();

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

  // Reservation column: guest + property + stay dates + nights (there's no
  // separate guest column here, so the guest is included).
  protected reservationLabel(id: number | undefined): string {
    if (id == null) {
      return '—';
    }
    const r = this.reservations().find((x) => x.id === Number(id));
    if (!r) {
      return `#${id}`;
    }
    return `${this.guestName(r.guestId)} · ${this.propertyTitle(r.propertyId)} · ${this.fmtDate(r.checkInDate)} → ${this.fmtDate(r.checkOutDate, true)} · ${r.nights} night${r.nights === 1 ? '' : 's'}`;
  }

  /** "2026-07-01" → "Jul 1" (or "Jul 1, 2026" with the year). */
  private fmtDate(d: string | undefined, withYear = false): string {
    if (!d) {
      return '';
    }
    const dt = new Date(String(d).slice(0, 10) + 'T00:00:00');
    return dt.toLocaleDateString('en-US', withYear ? { month: 'short', day: 'numeric', year: 'numeric' } : { month: 'short', day: 'numeric' });
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
