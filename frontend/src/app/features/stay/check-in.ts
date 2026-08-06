import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { forkJoin, of, catchError } from 'rxjs';
import { CrudService } from '../../core/services/crud.service';
import { ToastService } from '../../core/services/toast.service';
import { AuthService } from '../../core/auth/auth.service';
import { CheckInRecordResponse, GuestProfileResponse, PropertyResponse, ReservationResponse } from '../../core/models/dtos';
import { ACCESS_METHODS, CHECK_IN_STATUSES } from '../../core/models/enums';
import { LabelizePipe } from '../../shared/pipes/labelize.pipe';
import { OwnerPageHeaderComponent } from '../../shared/ui/owner-page-header';
import { OwnerDialogComponent } from '../../shared/ui/owner-dialog';
import { SelectValueDirective } from '../../shared/ui/select-value';

/**
 * Check-in screen (Stay domain) — a bespoke, self-contained CRUD screen for
 * /api/check-ins that mirrors the backend `stay` module's CheckInRecord. It
 * lists every check-in record and creates / edits / deletes them through a
 * modal form; the reservation and guest are chosen from dropdowns loaded from
 * their own endpoints so an operator never types a raw id.
 *
 * Choosing a reservation auto-fills the guest, since a reservation already names
 * exactly one guest — the picker is left editable only so an operator can correct
 * a mis-linked record.
 *
 * The generic engine could render this from `check-in.resource.ts`; it is
 * hand-written here so the Stay module owns an explicit, walk-through component
 * per screen — the same shape as `review-analytics`.
 */
@Component({
  selector: 'app-check-in',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, DatePipe, LabelizePipe, OwnerPageHeaderComponent, OwnerDialogComponent, SelectValueDirective],
  templateUrl: './check-in.html',
})
export class CheckInComponent {
  private fb = inject(FormBuilder);
  private crud = inject(CrudService);
  private toast = inject(ToastService);
  private auth = inject(AuthService);

  private readonly api = '/api/check-ins';

  // For a PROPERTY_MANAGER: the reservation ids at their own properties. Records
  // and the reservation picker are limited to these (null = no scoping).
  private readonly scopeResIds = signal<Set<number> | null>(null);

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
  protected readonly properties = signal<PropertyResponse[]>([]);

  protected readonly modalOpen = signal(false);
  protected readonly editingId = signal<number | null>(null);
  protected readonly deleteTarget = signal<CheckInRecordResponse | null>(null);

  // Every <select> on this screen is signal-backed rather than a reactive
  // control, and the template binds them through SelectValueDirective — which is
  // what guarantees a chosen value survives the option lists resolving and never
  // silently falls back to the default. `attempted` reveals the required-field
  // messages only after a submit try.
  protected readonly selectedReservationId = signal<number | null>(null);
  protected readonly selectedGuestId = signal<number | null>(null);
  protected readonly attempted = signal(false);

  protected readonly selectedAccessMethod = signal<string>('');
  protected readonly selectedStatus = signal<string>('PENDING');

  protected form: FormGroup = this.buildForm();

  protected readonly filtered = computed(() => {
    const scope = this.scopeResIds();
    const rows = scope ? this.rows().filter((r) => scope.has(r.reservationId)) : this.rows();
    const term = this.search().trim().toLowerCase();
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
   * list endpoint: the list is scoped for a PROPERTY_MANAGER to only their own
   * assigned properties, so a manager viewing a check-in for another property
   * would otherwise see "#id". Fetching by id is unscoped and works for every
   * role. Failures fall back silently (title resolves to "#id").
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

  protected propertyTitle(id: number | undefined): string {
    if (id == null) {
      return '—';
    }
    const p = this.properties().find((x) => x.id === Number(id));
    return p ? p.title : `#${id}`;
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

  /**
   * A reservation belongs to exactly one guest, so picking the reservation fills
   * the guest in for the operator instead of making them re-derive it. Clearing
   * the reservation clears the guest too, so the pair can't drift out of step.
   */
  protected onReservationChange(value: string): void {
    const id = value ? Number(value) : null;
    this.selectedReservationId.set(id);
    const reservation = id == null ? null : this.reservations().find((r) => r.id === id);
    this.selectedGuestId.set(reservation?.guestId ?? null);
  }

  protected onGuestChange(value: string): void {
    this.selectedGuestId.set(value ? Number(value) : null);
  }

  protected onAccessMethodChange(value: string): void {
    this.selectedAccessMethod.set(value);
  }

  protected onStatusChange(value: string): void {
    this.selectedStatus.set(value);
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

  // Reservation column: property + stay dates + nights (the guest has its own
  // column here, so it isn't repeated).
  protected reservationLabel(id: number | undefined): string {
    if (id == null) {
      return '—';
    }
    const r = this.reservations().find((x) => x.id === Number(id));
    if (!r) {
      return `#${id}`;
    }
    return `${this.propertyTitle(r.propertyId)} · ${this.fmtDate(r.checkInDate)} → ${this.fmtDate(r.checkOutDate, true)} · ${r.nights} night${r.nights === 1 ? '' : 's'}`;
  }

  /** "2026-07-01" → "Jul 1" (or "Jul 1, 2026" with the year). */
  private fmtDate(d: string | undefined, withYear = false): string {
    if (!d) {
      return '';
    }
    const dt = new Date(String(d).slice(0, 10) + 'T00:00:00');
    return dt.toLocaleDateString('en-US', withYear ? { month: 'short', day: 'numeric', year: 'numeric' } : { month: 'short', day: 'numeric' });
  }

  protected guestLabel(id: number | undefined): string {
    if (id == null) {
      return '—';
    }
    const g = this.guests().find((x) => x.id === Number(id));
    return g ? g.name : `#${id}`;
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
