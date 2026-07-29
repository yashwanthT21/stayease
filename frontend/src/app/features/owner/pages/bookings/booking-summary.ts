import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { OwnerDataService } from '../../data/owner-data.service';
import { ReservationResponse } from '../../../../core/models/dtos';
import { RESERVATION_STATUSES, ReservationStatus } from '../../../../core/models/enums';
import { LabelizePipe } from '../../../../shared/pipes/labelize.pipe';
import { OwnerPageHeaderComponent } from '../../ui/owner-page-header';
import { StatTileComponent } from '../../ui/stat-tile';

interface BookingRow {
  reservation: ReservationResponse;
  propertyTitle: string;
}

/** Statuses whose revenue "counts" (excludes cancellations / no-shows). */
const EARNING_STATUSES: ReservationStatus[] = ['CONFIRMED', 'ACTIVE', 'CHECKED_OUT'];

/**
 * Portfolio-wide booking overview: revenue and reservation KPIs rolled up
 * across every property the owner holds, plus a filterable reservation table.
 */
@Component({
  selector: 'app-owner-booking-summary',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [LabelizePipe, CurrencyPipe, OwnerPageHeaderComponent, StatTileComponent],
  templateUrl: './booking-summary.html',
})
export class BookingSummaryComponent {
  private data = inject(OwnerDataService);

  protected readonly statuses = RESERVATION_STATUSES;
  protected readonly loading = signal(true);
  protected readonly rows = signal<BookingRow[]>([]);
  protected readonly statusFilter = signal<'ALL' | ReservationStatus>('ALL');

  private readonly today = new Date().toISOString().slice(0, 10);

  protected readonly filtered = computed(() => {
    const f = this.statusFilter();
    const rows = this.rows();
    return f === 'ALL' ? rows : rows.filter((r) => r.reservation.status === f);
  });

  protected readonly totalBookings = computed(() => this.rows().length);

  protected readonly totalRevenue = computed(() =>
    this.rows()
      .filter((r) => EARNING_STATUSES.includes(r.reservation.status))
      .reduce((sum, r) => sum + Number(r.reservation.totalAmount ?? 0), 0),
  );

  protected readonly totalNights = computed(() =>
    this.rows()
      .filter((r) => EARNING_STATUSES.includes(r.reservation.status))
      .reduce((sum, r) => sum + Number(r.reservation.nights ?? 0), 0),
  );

  protected readonly upcoming = computed(() =>
    this.rows().filter(
      (r) => r.reservation.checkInDate >= this.today && (r.reservation.status === 'PENDING' || r.reservation.status === 'CONFIRMED'),
    ).length,
  );

  protected readonly statusCounts = computed(() => {
    const counts: Record<string, number> = {};
    for (const s of this.statuses) {
      counts[s] = 0;
    }
    for (const r of this.rows()) {
      counts[r.reservation.status] = (counts[r.reservation.status] ?? 0) + 1;
    }
    return counts;
  });

  constructor() {
    this.data.myReservations().subscribe({
      next: (groups) => {
        const flat: BookingRow[] = groups.flatMap((g) =>
          g.reservations.map((reservation) => ({ reservation, propertyTitle: g.property.title })),
        );
        // Newest check-in first.
        flat.sort((a, b) => b.reservation.checkInDate.localeCompare(a.reservation.checkInDate));
        this.rows.set(flat);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  protected setFilter(value: 'ALL' | ReservationStatus): void {
    this.statusFilter.set(value);
  }

  protected badge(status: string): string {
    switch (status) {
      case 'CONFIRMED':
      case 'ACTIVE':
      case 'CHECKED_OUT':
        return 'text-bg-success';
      case 'CANCELLED':
      case 'NO_SHOW':
        return 'text-bg-danger';
      default:
        return 'text-bg-secondary';
    }
  }

  protected isUpcoming(r: ReservationResponse): boolean {
    return r.checkInDate >= this.today && (r.status === 'PENDING' || r.status === 'CONFIRMED');
  }
}
