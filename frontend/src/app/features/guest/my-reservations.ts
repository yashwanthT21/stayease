import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { forkJoin } from 'rxjs';
import { ToastService } from '../../core/services/toast.service';
import { ReservationResponse } from '../../core/models/dtos';
import { LabelizePipe } from '../../shared/pipes/labelize.pipe';
import { OwnerDialogComponent } from '../owner/ui/owner-dialog';
import { BookingService, ReviewInput } from './booking.service';

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
  imports: [CurrencyPipe, LabelizePipe, OwnerDialogComponent],
  templateUrl: './my-reservations.html',
})
export class MyReservationsComponent {
  private booking = inject(BookingService);
  private toast = inject(ToastService);

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
