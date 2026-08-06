import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { forkJoin } from 'rxjs';
import { ToastService } from '../../../core/services/toast.service';
import { CrudService } from '../../../core/services/crud.service';
import { CheckInRecordResponse, CheckOutRecordResponse, PropertyResponse, ReservationResponse } from '../../../core/models/dtos';
import { LabelizePipe } from '../../../shared/pipes/labelize.pipe';
import { OwnerDialogComponent } from '../../../shared/ui/owner-dialog';
import { BookingService, ReviewInput } from '../shared/booking.service';

interface Category {
  key: 'cleanliness' | 'accuracy' | 'location' | 'value';
  label: string;
}

/**
 * The guest's own reservations with their approval status, plus a "Leave a
 * review" action on checked-out stays (one review per reservation).
 */
@Component({
  selector: 'app-my-reservations',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CurrencyPipe, DatePipe, LabelizePipe, OwnerDialogComponent],
  templateUrl: './my-reservations.html',
})
export class MyReservationsComponent {
  private booking = inject(BookingService);
  private toast = inject(ToastService);
  private crud = inject(CrudService);

  /** propertyId → title, so the table shows the property name, not "#id". */
  protected readonly propertyNames = signal<Map<number, string>>(new Map());
  /** reservationId → actual arrival / departure timestamps (from the stay records). */
  protected readonly checkInAt = signal<Map<number, string>>(new Map());
  protected readonly checkOutAt = signal<Map<number, string>>(new Map());

  protected readonly stars = [1, 2, 3, 4, 5];
  protected readonly categories: Category[] = [
    { key: 'cleanliness', label: 'Cleanliness' },
    { key: 'accuracy', label: 'Accuracy' },
    { key: 'location', label: 'Location' },
    { key: 'value', label: 'Value' },
  ];

  protected readonly loading = signal(true);
  protected readonly rows = signal<ReservationResponse[]>([]);
  protected readonly reviewedIds = signal<Set<number>>(new Set());
  protected readonly checkedOutIds = signal<Set<number>>(new Set());

  // Review modal state
  protected readonly reviewing = signal<ReservationResponse | null>(null);
  protected readonly submitting = signal(false);
  protected readonly scores = signal<Record<string, number>>({ cleanliness: 0, accuracy: 0, location: 0, value: 0 });
  protected readonly comments = signal('');

  constructor() {
    forkJoin([
      this.booking.myReservations(),
      this.booking.myReviews(),
      this.booking.checkedOutReservationIds(),
    ]).subscribe({
      next: ([reservations, reviews, checkedOut]) => {
        this.rows.set([...reservations].sort((a, b) => b.checkInDate.localeCompare(a.checkInDate)));
        this.reviewedIds.set(new Set(reviews.map((r) => r.reservationId)));
        this.checkedOutIds.set(new Set(checkedOut));
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });

    // Resolve property ids to names for the table (read-only lookup).
    this.crud.list<PropertyResponse>('/api/properties').subscribe({
      next: (props) => this.propertyNames.set(new Map(props.map((p) => [p.id, p.title]))),
      error: () => {},
    });

    // Actual arrival times (guests may read check-in records).
    this.crud.list<CheckInRecordResponse>('/api/check-ins').subscribe({
      next: (recs) =>
        this.checkInAt.set(new Map(recs.filter((r) => r.actualCheckIn).map((r) => [r.reservationId, r.actualCheckIn as string]))),
      error: () => {},
    });
    // Actual departure times (from check-out records).
    this.crud.list<CheckOutRecordResponse>('/api/check-outs').subscribe({
      next: (recs) =>
        this.checkOutAt.set(new Map(recs.filter((r) => r.actualCheckOut).map((r) => [r.reservationId, r.actualCheckOut as string]))),
      error: () => {},
    });
  }

  protected propertyName(id: number): string {
    return this.propertyNames().get(id) ?? `#${id}`;
  }

  protected actualCheckIn(r: ReservationResponse): string | null {
    return this.checkInAt().get(r.id) ?? null;
  }
  protected actualCheckOut(r: ReservationResponse): string | null {
    return this.checkOutAt().get(r.id) ?? null;
  }

  /** Progress of the stay itself (separate from the booking's approval status). */
  protected stayState(r: ReservationResponse): 'COMPLETED' | 'CHECKED_IN' | 'NOT_CHECKED_IN' {
    if (this.checkOutAt().has(r.id) || r.status === 'CHECKED_OUT') {
      return 'COMPLETED';
    }
    if (this.checkInAt().has(r.id) || r.status === 'ACTIVE') {
      return 'CHECKED_IN';
    }
    return 'NOT_CHECKED_IN';
  }

  protected stayLabel(r: ReservationResponse): string {
    switch (this.stayState(r)) {
      case 'COMPLETED':
        return 'Completed';
      case 'CHECKED_IN':
        return 'Checked in';
      default:
        return 'Not checked in';
    }
  }

  protected stayBadge(r: ReservationResponse): string {
    switch (this.stayState(r)) {
      case 'COMPLETED':
        return 'text-bg-success';
      case 'CHECKED_IN':
        return 'text-bg-info';
      default:
        return 'text-bg-secondary';
    }
  }

  /** Reviewable once the stay is checked out (a check-out record exists, or the
   *  reservation itself is marked CHECKED_OUT) and not yet reviewed. */
  protected canReview(r: ReservationResponse): boolean {
    const checkedOut = this.checkedOutIds().has(r.id) || r.status === 'CHECKED_OUT';
    return checkedOut && !this.reviewedIds().has(r.id);
  }

  protected isReviewed(r: ReservationResponse): boolean {
    return this.reviewedIds().has(r.id);
  }

  protected openReview(r: ReservationResponse): void {
    this.reviewing.set(r);
    this.scores.set({ cleanliness: 0, accuracy: 0, location: 0, value: 0 });
    this.comments.set('');
  }

  protected closeReview(): void {
    this.reviewing.set(null);
  }

  protected setScore(category: string, value: number): void {
    this.scores.update((s) => ({ ...s, [category]: value }));
  }

  protected onComments(event: Event): void {
    this.comments.set((event.target as HTMLTextAreaElement).value);
  }

  protected submitReview(): void {
    const reservation = this.reviewing();
    if (!reservation) {
      return;
    }
    const s = this.scores();
    if (!s['cleanliness'] && !s['accuracy'] && !s['location'] && !s['value']) {
      this.toast.error('Please rate at least one category.');
      return;
    }
    const input: ReviewInput = { reservationId: reservation.id, guestId: reservation.guestId };
    if (s['cleanliness']) input.cleanlinessScore = s['cleanliness'];
    if (s['accuracy']) input.accuracyScore = s['accuracy'];
    if (s['location']) input.locationScore = s['location'];
    if (s['value']) input.valueScore = s['value'];
    if (this.comments().trim()) input.comments = this.comments().trim();

    this.submitting.set(true);
    this.booking.submitReview(input).subscribe({
      next: () => {
        this.submitting.set(false);
        this.reviewedIds.update((set) => new Set(set).add(reservation.id));
        this.reviewing.set(null);
        this.toast.success('Thanks! Your review has been posted.');
      },
      error: () => this.submitting.set(false),
    });
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

  protected statusLabel(status: string): string {
    return status === 'CANCELLED' ? 'Rejected' : status;
  }
}
