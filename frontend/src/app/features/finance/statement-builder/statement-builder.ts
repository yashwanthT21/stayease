import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { forkJoin, of, switchMap, map, catchError } from 'rxjs';
import { CrudService } from '../../../core/services/crud.service';
import { ToastService } from '../../../core/services/toast.service';
import {
  CheckOutRecordResponse,
  MaintenanceIssueResponse,
  OwnerStatementResponse,
  PropertyResponse,
  ReservationResponse,
  UserResponse,
} from '../../../core/models/dtos';
import { STATEMENT_STATUS_OPTIONS } from '../../../core/models/enums';
import { LabelizePipe } from '../../../shared/pipes/labelize.pipe';
import { formatRupees } from '../../../shared/money';
import { OwnerDialogComponent } from '../../../shared/ui/owner-dialog';
import { SelectValueDirective } from '../../../shared/ui/select-value';

/**
 * Finance → Owner Statements. Lists issued statements and generates new ones.
 *
 * The "New Statement" builder derives the money fields from real records rather
 * than asking Finance to key them in by hand:
 *   - Gross revenue    = Σ baseAmount of the owner's live bookings (confirmed
 *                        onward) whose check-out date falls in the chosen period.
 *   - Cleaning fee      = baseCleaningCost × number of actual check-out records
 *                        (this app keeps reservation.status at CONFIRMED and
 *                        records the checkout in check_out_records instead).
 *   - Maintenance cost = Σ amountSpent of the owner's RESOLVED/CLOSED
 *                        maintenance issues resolved in the period.
 *   - Platform fee      = grossRevenue × platformFeePct%  (entered as a %).
 *   - Management fee    = entered directly as a flat amount.
 * The backend still owns the netPayout arithmetic; we only POST the components.
 *
 * The owner is CHOSEN from a list rather than keyed in as a user id: a mistyped id
 * silently posts a whole month's money against the wrong person, and Finance has
 * no way to spot it from a bare number.
 *
 * A statement's APPROVED/REJECTED states belong to the owner, not to Finance, so
 * they aren't offered in the status dropdown here — see STATEMENT_STATUS_OPTIONS.
 */
@Component({
  selector: 'app-statement-builder',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [LabelizePipe, OwnerDialogComponent, SelectValueDirective],
  templateUrl: './statement-builder.html',
})
export class StatementBuilderComponent {
  private crud = inject(CrudService);
  private toast = inject(ToastService);

  protected readonly statuses = STATEMENT_STATUS_OPTIONS;

  /** OWNER users, for the picker. Empty (e.g. 403) falls back to a number input. */
  protected readonly owners = signal<UserResponse[]>([]);

  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly modalOpen = signal(false);

  protected readonly statements = signal<OwnerStatementResponse[]>([]);
  protected readonly deleteTarget = signal<OwnerStatementResponse | null>(null);

  // ---- edit modal state ----
  protected readonly editTarget = signal<OwnerStatementResponse | null>(null);
  protected readonly savingEdit = signal(false);
  protected readonly editGross = signal(0);
  protected readonly editPlatform = signal(0);
  protected readonly editManagement = signal(0);
  protected readonly editCleaning = signal(0);
  protected readonly editMaintenance = signal(0);
  protected readonly editStatus = signal<string>('DRAFT');
  protected readonly editNet = computed(() =>
    round2(
      this.editGross() +
        this.editCleaning() -
        this.editPlatform() -
        this.editManagement() -
        this.editMaintenance(),
    ),
  );

  // ---- builder inputs (signals for zoneless-reliable binding) ----
  protected readonly triedSubmit = signal(false);
  protected readonly ownerId = signal<number | null>(null);
  protected readonly period = signal(''); // 'YYYY-MM'
  protected readonly baseCleaningCost = signal(50);
  protected readonly platformFeePct = signal(3);
  protected readonly managementFee = signal(0);
  protected readonly status = signal<string>('DRAFT');

  // ---- values derived from records (populated by calculate()) ----
  protected readonly calculating = signal(false);
  protected readonly calculated = signal(false);
  protected readonly grossRevenue = signal(0);
  protected readonly revenueCount = signal(0); // live bookings counted toward gross
  protected readonly cleaningCount = signal(0); // number of checkouts in the period
  protected readonly maintenanceCost = signal(0);

  // ---- live-derived money fields ----
  protected readonly cleaningFee = computed(() => round2(this.baseCleaningCost() * this.cleaningCount()));
  protected readonly platformFee = computed(() => round2(this.grossRevenue() * (this.platformFeePct() / 100)));
  protected readonly netPayout = computed(() =>
    round2(
      this.grossRevenue() +
        this.cleaningFee() -
        this.platformFee() -
        this.managementFee() -
        this.maintenanceCost(),
    ),
  );

  protected readonly periodValid = computed(() => /^\d{4}-(0[1-9]|1[0-2])$/.test(this.period()));
  protected readonly formValid = computed(() => !!this.ownerId() && this.periodValid());

  constructor() {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.crud.list<OwnerStatementResponse>('/api/owner-statements').subscribe({
      next: (rows) => {
        this.statements.set([...rows].sort((a, b) => b.period.localeCompare(a.period)));
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
    // Separate call, and deliberately non-fatal: if the owner list can't be read
    // the builder still works, it just falls back to typing the id.
    this.crud.list<UserResponse>('/api/users/owners').subscribe({
      next: (rows) => this.owners.set(rows),
      error: () => this.owners.set([]),
    });
  }

  /** "Ada Owner (ada@example.com)" — enough to tell two same-named owners apart. */
  protected ownerLabel(owner: UserResponse): string {
    return owner.email ? `${owner.name} (${owner.email})` : owner.name;
  }

  /** The chosen owner's name, for the statement list and the confirm step. */
  protected ownerName(ownerId: number | null | undefined): string {
    if (ownerId == null) {
      return '—';
    }
    const owner = this.owners().find((o) => o.id === ownerId);
    return owner ? owner.name : `#${ownerId}`;
  }

  // ---------------- builder modal ----------------
  protected openCreate(): void {
    this.triedSubmit.set(false);
    this.ownerId.set(null);
    this.period.set('');
    this.baseCleaningCost.set(50);
    this.platformFeePct.set(3);
    this.managementFee.set(0);
    this.status.set('DRAFT');
    this.resetCalc();
    this.modalOpen.set(true);
  }

  protected closeModal(): void {
    this.modalOpen.set(false);
  }

  private resetCalc(): void {
    this.calculated.set(false);
    this.grossRevenue.set(0);
    this.revenueCount.set(0);
    this.cleaningCount.set(0);
    this.maintenanceCost.set(0);
  }

  // Owner/period changes invalidate any prior calculation (they change the
  // record set); rate inputs only feed the reactive computed fields, so they
  // don't require a re-fetch.
  /** From the picker (or the id fallback input) — both hand over a string. */
  protected onOwnerId(value: string): void {
    this.ownerId.set(value ? Number(value) : null);
    this.resetCalc();
  }

  protected onOwnerIdInput(e: Event): void {
    this.onOwnerId((e.target as HTMLInputElement).value);
  }
  protected onPeriod(e: Event): void {
    this.period.set((e.target as HTMLInputElement).value);
    this.resetCalc();
  }
  protected onBaseCleaning(e: Event): void {
    this.baseCleaningCost.set(toNumber((e.target as HTMLInputElement).value));
  }
  protected onPlatformPct(e: Event): void {
    this.platformFeePct.set(toNumber((e.target as HTMLInputElement).value));
  }
  protected onManagementFee(e: Event): void {
    this.managementFee.set(toNumber((e.target as HTMLInputElement).value));
  }
  protected onStatus(value: string): void {
    this.status.set(value);
  }

  /** Fetch the owner's properties → reservations + maintenance, and total them up. */
  protected calculate(): void {
    this.triedSubmit.set(true);
    if (!this.formValid()) {
      return;
    }
    const ownerId = this.ownerId()!;
    const period = this.period();

    this.calculating.set(true);
    this.crud
      .list<PropertyResponse>('/api/properties', { ownerId })
      .pipe(
        switchMap((props) => {
          if (!props.length) {
            return of({
              reservations: [] as ReservationResponse[],
              issues: [] as MaintenanceIssueResponse[],
              checkouts: [] as CheckOutRecordResponse[],
            });
          }
          return forkJoin({
            reservations: forkJoin(
              props.map((p) =>
                this.crud
                  .list<ReservationResponse>('/api/reservations', { propertyId: p.id })
                  .pipe(catchError(() => of([] as ReservationResponse[]))),
              ),
            ).pipe(map((lists) => lists.flat())),
            issues: forkJoin(
              props.map((p) =>
                this.crud
                  .list<MaintenanceIssueResponse>('/api/maintenance-issues', { propertyId: p.id })
                  .pipe(catchError(() => of([] as MaintenanceIssueResponse[]))),
              ),
            ).pipe(map((lists) => lists.flat())),
            // Checkouts live in their own table, keyed by reservation (not scoped
            // by property on the API), so fetch all and filter to this owner's.
            checkouts: this.crud
              .list<CheckOutRecordResponse>('/api/check-outs')
              .pipe(catchError(() => of([] as CheckOutRecordResponse[]))),
          });
        }),
      )
      .subscribe({
        next: (d) => {
          const ownerReservationIds = new Set(d.reservations.map((r) => r.id));
          // Gross counts every live booking (confirmed onward) whose stay ends in
          // the period; cancelled/pending/no-show don't earn.
          const earning = d.reservations.filter(
            (r) => REVENUE_STATUSES.has(r.status) && (r.checkOutDate ?? '').startsWith(period),
          );
          // Cleaning is billed per actual checkout — sourced from check_out_records
          // (the reservation's own status stays CONFIRMED after checkout here).
          const checkouts = d.checkouts.filter(
            (c) =>
              ownerReservationIds.has(c.reservationId) &&
              (c.actualCheckOut ?? '').startsWith(period),
          );
          const resolved = d.issues.filter(
            (i) =>
              (i.status === 'RESOLVED' || i.status === 'CLOSED') &&
              (i.resolvedDate ?? '').startsWith(period),
          );
          this.grossRevenue.set(round2(earning.reduce((s, r) => s + Number(r.baseAmount ?? 0), 0)));
          this.revenueCount.set(earning.length);
          this.cleaningCount.set(checkouts.length);
          this.maintenanceCost.set(round2(resolved.reduce((s, i) => s + Number(i.amountSpent ?? 0), 0)));
          this.calculated.set(true);
          this.calculating.set(false);
        },
        error: () => this.calculating.set(false),
      });
  }

  protected submit(): void {
    this.triedSubmit.set(true);
    if (!this.formValid() || !this.calculated()) {
      return;
    }
    const payload: Record<string, unknown> = {
      ownerId: this.ownerId(),
      period: this.period(),
      grossRevenue: this.grossRevenue(),
      platformFee: this.platformFee(),
      managementFee: this.managementFee(),
      cleaningRevenue: this.cleaningFee(),
      maintenanceCost: this.maintenanceCost(),
      status: this.status(),
    };

    this.saving.set(true);
    this.crud.create<OwnerStatementResponse>('/api/owner-statements', payload).subscribe({
      next: () => {
        this.saving.set(false);
        this.modalOpen.set(false);
        this.toast.success('Statement generated.');
        this.load();
      },
      error: () => this.saving.set(false),
    });
  }

  // ---------------- edit ----------------
  protected openEdit(row: OwnerStatementResponse): void {
    this.editTarget.set(row);
    this.editGross.set(Number(row.grossRevenue ?? 0));
    this.editPlatform.set(Number(row.platformFee ?? 0));
    this.editManagement.set(Number(row.managementFee ?? 0));
    this.editCleaning.set(Number(row.cleaningRevenue ?? 0));
    this.editMaintenance.set(Number(row.maintenanceCost ?? 0));
    // APPROVED / REJECTED aren't Finance's to set, so they're not in the dropdown.
    // Opening a decided statement therefore lands on ISSUED — which is exactly the
    // re-issue action: saving puts the corrected figures back to the owner and
    // clears their old answer (see the backend's OwnerStatementMapper).
    this.editStatus.set(this.isDecided(row) ? 'ISSUED' : row.status);
  }

  /** The owner has already answered this statement (approved or rejected). */
  protected isDecided(row: OwnerStatementResponse): boolean {
    return row.status === 'APPROVED' || row.status === 'REJECTED';
  }

  /** An owner rejected it — Finance needs to correct and re-issue. */
  protected isRejected(row: OwnerStatementResponse): boolean {
    return row.status === 'REJECTED';
  }

  /** Payout is unlocked only once the owner has approved. */
  protected isPayable(row: OwnerStatementResponse): boolean {
    return row.status === 'APPROVED';
  }

  /** Plain-English answer to "can I pay this yet?", shown in the list. */
  protected payoutHint(row: OwnerStatementResponse): string {
    switch (row.status) {
      case 'APPROVED':
        return 'Owner approved — payout can be released.';
      case 'REJECTED':
        return 'Owner rejected — correct the figures and re-issue.';
      case 'ISSUED':
        return 'Waiting on the owner to approve.';
      case 'PAID':
        return 'Payout already released.';
      default:
        return 'Draft — issue it to send it to the owner.';
    }
  }

  protected closeEdit(): void {
    this.editTarget.set(null);
  }

  protected onEditGross(e: Event): void {
    this.editGross.set(toNumber((e.target as HTMLInputElement).value));
  }
  protected onEditPlatform(e: Event): void {
    this.editPlatform.set(toNumber((e.target as HTMLInputElement).value));
  }
  protected onEditManagement(e: Event): void {
    this.editManagement.set(toNumber((e.target as HTMLInputElement).value));
  }
  protected onEditCleaning(e: Event): void {
    this.editCleaning.set(toNumber((e.target as HTMLInputElement).value));
  }
  protected onEditMaintenance(e: Event): void {
    this.editMaintenance.set(toNumber((e.target as HTMLInputElement).value));
  }
  protected onEditStatus(value: string): void {
    this.editStatus.set(value);
  }

  protected saveEdit(): void {
    const row = this.editTarget();
    if (!row) {
      return;
    }
    const payload: Record<string, unknown> = {
      ownerId: row.ownerId,
      period: row.period,
      grossRevenue: this.editGross(),
      platformFee: this.editPlatform(),
      managementFee: this.editManagement(),
      cleaningRevenue: this.editCleaning(),
      maintenanceCost: this.editMaintenance(),
      status: this.editStatus(),
    };

    this.savingEdit.set(true);
    this.crud.update<OwnerStatementResponse>('/api/owner-statements', row.id, payload).subscribe({
      next: () => {
        this.savingEdit.set(false);
        this.editTarget.set(null);
        this.toast.success('Statement updated.');
        this.load();
      },
      error: () => this.savingEdit.set(false),
    });
  }

  // ---------------- delete ----------------
  protected askDelete(row: OwnerStatementResponse): void {
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
    this.crud.remove('/api/owner-statements', row.id).subscribe({
      next: () => {
        this.deleteTarget.set(null);
        this.toast.success('Statement deleted.');
        this.load();
      },
      error: () => this.deleteTarget.set(null),
    });
  }

  // ---------------- display helpers ----------------
  protected money(value: unknown): string {
    return formatRupees(value);
  }

  protected badge(status: string): string {
    switch (status) {
      case 'PAID':
      case 'APPROVED':
        return 'text-bg-success';
      case 'REJECTED':
        return 'text-bg-danger';
      case 'ISSUED':
        return 'text-bg-warning';
      default:
        return 'text-bg-secondary';
    }
  }
}

/** Reservation statuses that represent earned revenue (excludes pending/cancelled/no-show). */
const REVENUE_STATUSES = new Set(['CONFIRMED', 'ACTIVE', 'CHECKED_OUT']);

function round2(n: number): number {
  return Math.round((Number.isFinite(n) ? n : 0) * 100) / 100;
}

function toNumber(v: string): number {
  const n = Number(v);
  return Number.isFinite(n) && n >= 0 ? n : 0;
}
