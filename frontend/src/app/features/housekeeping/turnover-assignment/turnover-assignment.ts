import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { forkJoin, map, of, switchMap, catchError } from 'rxjs';
import { CrudService } from '../../../core/services/crud.service';
import { ToastService } from '../../../core/services/toast.service';
import { AuthService } from '../../../core/auth/auth.service';
import {
  CheckOutRecordResponse,
  PropertyResponse,
  ReservationResponse,
  TurnoverAssignmentResponse,
  UserResponse,
} from '../../../core/models/dtos';
import { HOUSEKEEPER_STATUSES, TURNOVER_STATUSES } from '../../../core/models/enums';
import { LabelizePipe } from '../../../shared/pipes/labelize.pipe';
import { OwnerDialogComponent } from '../../../shared/ui/owner-dialog';
import { SelectValueDirective } from '../../../shared/ui/select-value';
import { TurnoverChecklistComponent } from '../turnover-checklist/turnover-checklist';

/**
 * Turnover assignment screen (Housekeeping domain): lists turnover assignments
 * and creates new ones.
 *
 * A turnover can only be created for a stay that has been CHECKED OUT — so the
 * "Reservation" picker is fed only by reservations that have a check-out record.
 * Choosing a reservation auto-fills its property; the manager then assigns a
 * housekeeper and schedules the clean. Viewing a turnover's checklist is
 * delegated to the child TurnoverChecklistComponent (a modal).
 */
@Component({
  selector: 'app-turnover-assignment',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [LabelizePipe, OwnerDialogComponent, TurnoverChecklistComponent, SelectValueDirective],
  templateUrl: './turnover-assignment.html',
})
export class TurnoverAssignmentComponent {
  private crud = inject(CrudService);
  private toast = inject(ToastService);
  private auth = inject(AuthService);

  protected readonly statuses = TURNOVER_STATUSES;
  protected readonly managerStatuses = TURNOVER_STATUSES;
  protected readonly housekeeperStatuses = HOUSEKEEPER_STATUSES;
  /** Only managers/admins create turnovers; housekeepers just view (and complete) theirs. */
  protected readonly canCreate = computed(() => {
    const r = this.auth.role();
    return r === 'PROPERTY_MANAGER' || r === 'ADMIN';
  });

  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly modalOpen = signal(false);

  protected readonly properties = signal<PropertyResponse[]>([]);
  protected readonly checkedOutReservations = signal<ReservationResponse[]>([]);
  protected readonly housekeepers = signal<UserResponse[]>([]);
  protected readonly turnovers = signal<TurnoverAssignmentResponse[]>([]);

  // ---- create-form state (signals for zoneless-reliable selects) ----
  protected readonly triedSubmit = signal(false);
  protected readonly reservationId = signal<number | null>(null);
  protected readonly housekeeperId = signal<number | null>(null);
  protected readonly status = signal<string>('PENDING');
  protected readonly assignedDate = signal('');
  protected readonly startByTime = signal('');
  protected readonly completeByTime = signal('');

  // ---- "view checklist" modal: which turnover is open. The loading + items
  //      live in the child TurnoverChecklistComponent; here we only track the
  //      selection and its header title. ----
  protected readonly checklistFor = signal<TurnoverAssignmentResponse | null>(null);
  protected readonly checklistTitle = computed(() => {
    const t = this.checklistFor();
    return t ? this.propertyTitle(t.propertyId) : '';
  });

  protected readonly selectedReservation = computed(
    () => this.checkedOutReservations().find((r) => r.id === this.reservationId()) ?? null,
  );
  protected readonly selectedPropertyTitle = computed(() => {
    const r = this.selectedReservation();
    return r ? this.propertyTitle(r.propertyId) : '';
  });

  constructor() {
    this.load();
  }

  private load(): void {
    this.loading.set(true);

    // Housekeepers just see the turnovers assigned to them (no create tooling).
    if (!this.canCreate()) {
      const userId = this.auth.user()?.userId ?? 0;
      forkJoin({
        properties: this.crud.list<PropertyResponse>('/api/properties').pipe(catchError(() => of([] as PropertyResponse[]))),
        turnovers: this.crud
          .list<TurnoverAssignmentResponse>('/api/turnovers', { assignedToId: userId })
          .pipe(catchError(() => of([] as TurnoverAssignmentResponse[]))),
      }).subscribe({
        next: (d) => {
          this.properties.set(d.properties);
          this.turnovers.set(d.turnovers);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
      return;
    }

    this.crud
      .list<PropertyResponse>('/api/properties')
      .pipe(
        switchMap((props) => {
          const propIds = new Set(props.map((p) => p.id));
          return forkJoin({
            properties: of(props),
            propIds: of(propIds),
            checkOutIds: this.crud
              .list<CheckOutRecordResponse>('/api/check-outs')
              .pipe(map((cs) => new Set(cs.map((c) => c.reservationId))), catchError(() => of(new Set<number>()))),
            housekeepers: this.crud.list<UserResponse>('/api/users/housekeepers').pipe(catchError(() => of([] as UserResponse[]))),
            turnovers: this.crud.list<TurnoverAssignmentResponse>('/api/turnovers').pipe(catchError(() => of([] as TurnoverAssignmentResponse[]))),
            reservations: props.length
              ? forkJoin(
                  props.map((p) =>
                    this.crud.list<ReservationResponse>('/api/reservations', { propertyId: p.id }).pipe(catchError(() => of([] as ReservationResponse[]))),
                  ),
                ).pipe(map((lists) => lists.flat()))
              : of([] as ReservationResponse[]),
          });
        }),
      )
      .subscribe({
        next: (d) => {
          this.properties.set(d.properties);
          this.housekeepers.set(d.housekeepers);
          this.turnovers.set(d.turnovers.filter((t) => d.propIds.has(t.propertyId)));
          this.checkedOutReservations.set(d.reservations.filter((r) => d.checkOutIds.has(r.id)));
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  protected openCreate(): void {
    this.triedSubmit.set(false);
    this.reservationId.set(null);
    this.housekeeperId.set(null);
    this.status.set('PENDING');
    this.assignedDate.set('');
    this.startByTime.set('');
    this.completeByTime.set('');
    this.modalOpen.set(true);
  }

  protected closeModal(): void {
    this.modalOpen.set(false);
  }

  // Bound through SelectValueDirective: the reservation and housekeeper lists load
  // asynchronously, and without it a pick made before they settled reverted to the
  // placeholder while the signal still held the real choice.
  protected onReservation(value: string): void {
    this.reservationId.set(value ? Number(value) : null);
  }
  protected onHousekeeper(value: string): void {
    this.housekeeperId.set(value ? Number(value) : null);
  }
  protected onStatus(value: string): void {
    this.status.set(value);
  }
  protected onAssignedDate(e: Event): void {
    this.assignedDate.set((e.target as HTMLInputElement).value);
  }
  protected onStartBy(e: Event): void {
    this.startByTime.set((e.target as HTMLInputElement).value);
  }
  protected onCompleteBy(e: Event): void {
    this.completeByTime.set((e.target as HTMLInputElement).value);
  }

  protected submit(): void {
    this.triedSubmit.set(true);
    const reservation = this.selectedReservation();
    if (!reservation || !this.housekeeperId()) {
      return;
    }
    const payload: Record<string, unknown> = {
      propertyId: reservation.propertyId,
      checkOutReservationId: reservation.id,
      assignedToId: this.housekeeperId(),
      status: this.status(),
    };
    if (this.assignedDate()) payload['assignedDate'] = this.assignedDate();
    if (this.startByTime()) payload['startByTime'] = this.startByTime();
    if (this.completeByTime()) payload['completeByTime'] = this.completeByTime();

    this.saving.set(true);
    this.crud.create<TurnoverAssignmentResponse>('/api/turnovers', payload).subscribe({
      next: () => {
        this.saving.set(false);
        this.modalOpen.set(false);
        this.toast.success('Turnover created.');
        this.load();
      },
      error: () => this.saving.set(false),
    });
  }

  protected propertyTitle(id: number): string {
    return this.properties().find((p) => p.id === id)?.title ?? `#${id}`;
  }

  /** "2027-01-10T14:00:00" → "2027-01-10 14:00" (or — when absent). */
  protected fmtDateTime(dt: string | undefined): string {
    return dt ? String(dt).slice(0, 16).replace('T', ' ') : '—';
  }

  protected housekeeperName(id: number | undefined): string {
    if (id == null) {
      return '—';
    }
    const h = this.housekeepers().find((x) => x.id === id);
    return h ? h.name : `#${id}`;
  }

  protected badge(status: string): string {
    switch (status) {
      case 'COMPLETED':
        return 'text-bg-success';
      case 'IN_PROGRESS':
        return 'text-bg-warning';
      case 'ISSUE_REPORTED':
        return 'text-bg-danger';
      default:
        return 'text-bg-secondary';
    }
  }

  protected hkBadge(status: string): string {
    return status === 'COMPLETED' ? 'text-bg-success' : 'text-bg-secondary';
  }

  /** Housekeeper marks their own work Pending/Completed. */
  protected onHousekeeperStatus(t: TurnoverAssignmentResponse, value: string): void {
    if (value === t.housekeeperStatus) {
      return; // the directive re-asserting the current value, not a user change
    }
    this.crud
      .patch<TurnoverAssignmentResponse>(`/api/turnovers/${t.id}/housekeeper-status?value=${value}`)
      .subscribe({
        next: (updated) => {
          this.replaceTurnover(updated);
          this.toast.success('Your status was updated.');
        },
        error: () => {},
      });
  }

  /** Manager sets the overall status (backend rejects it until housekeeper is Completed). */
  protected onManagerStatus(t: TurnoverAssignmentResponse, value: string): void {
    if (value === t.status) {
      return; // no change
    }
    this.crud
      .patch<TurnoverAssignmentResponse>(`/api/turnovers/${t.id}/manager-status?value=${value}`)
      .subscribe({
        next: (updated) => {
          this.replaceTurnover(updated);
          this.toast.success('Turnover status updated.');
        },
        // A 400 (housekeeper not yet completed) is surfaced by the error interceptor.
        error: () => this.load(),
      });
  }

  private replaceTurnover(updated: TurnoverAssignmentResponse): void {
    this.turnovers.update((list) => list.map((x) => (x.id === updated.id ? updated : x)));
  }
}
