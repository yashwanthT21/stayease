import { Injectable, inject } from '@angular/core';
import { Observable, map, of, switchMap, throwError } from 'rxjs';
import { CrudService } from '../../core/services/crud.service';
import { AuthService } from '../../core/auth/auth.service';
import {
  AvailabilityCalendarResponse,
  CheckOutRecordResponse,
  GuestProfileResponse,
  GuestReviewResponse,
  PropertyResponse,
  ReservationResponse,
} from '../../core/models/dtos';

/** Fields collected by the guest's review form. */
export interface ReviewInput {
  reservationId: number;
  guestId: number;
  cleanlinessScore?: number;
  accuracyScore?: number;
  locationScore?: number;
  valueScore?: number;
  comments?: string;
}

export interface BookingInput {
  propertyId: number;
  checkInDate: string; // yyyy-MM-dd
  checkOutDate: string; // yyyy-MM-dd
  guestCount: number;
}

/** Thrown when at least one requested night is not AVAILABLE. */
export class NotAvailableError extends Error {
  constructor(public readonly date: string) {
    super(`Not available on ${date}`);
  }
}

/**
 * Customer (GUEST) booking — approval-based.
 *
 * A property can only be requested if EVERY night in the range currently has an
 * availability row marked AVAILABLE. On success we create a PENDING reservation;
 * the dates are NOT held yet — a manager holds them (marks BOOKED) only when
 * they APPROVE, via the backend PATCH /reservations/{id}/approve endpoint.
 */
@Injectable({ providedIn: 'root' })
export class BookingService {
  private crud = inject(CrudService);
  private auth = inject(AuthService);

  /** Properties a guest can browse and book (published only). */
  listedProperties(): Observable<PropertyResponse[]> {
    return this.crud
      .list<PropertyResponse>('/api/properties')
      .pipe(map((props) => props.filter((p) => p.status === 'LISTED')));
  }

  book(input: BookingInput): Observable<ReservationResponse> {
    const nights = this.nights(input.checkInDate, input.checkOutDate);
    if (!nights.length) {
      return throwError(() => new Error('Check-out must be after check-in.'));
    }
    return this.crud.list<AvailabilityCalendarResponse>('/api/availability', { propertyId: input.propertyId }).pipe(
      switchMap((entries) => {
        const byDate = new Map(entries.map((e) => [String(e.calendarDate).slice(0, 10), e]));
        const nightEntries: AvailabilityCalendarResponse[] = [];
        for (const d of nights) {
          const entry = byDate.get(d);
          if (!entry || entry.availabilityStatus !== 'AVAILABLE') {
            return throwError(() => new NotAvailableError(d));
          }
          nightEntries.push(entry);
        }
        const amount = nightEntries.reduce((sum, e) => sum + Number(e.basePrice), 0);

        return this.resolveGuestId().pipe(
          switchMap((guestId) =>
            this.crud.create<ReservationResponse>('/api/reservations', {
              propertyId: input.propertyId,
              guestId,
              checkInDate: input.checkInDate,
              checkOutDate: input.checkOutDate,
              guestCount: input.guestCount,
              baseAmount: amount,
              bookingSource: 'PLATFORM',
              status: 'PENDING',
            }),
          ),
        );
      }),
    );
  }

  /** The signed-in guest's own reservations (empty if they have no profile yet). */
  myReservations(): Observable<ReservationResponse[]> {
    const userId = this.auth.user()?.userId ?? 0;
    return this.crud.list<GuestProfileResponse>('/api/guests', { userId }).pipe(
      switchMap((profiles) => {
        const mine = profiles.find((p) => p.userId === userId) ?? profiles[0];
        if (!mine) {
          return of([] as ReservationResponse[]);
        }
        return this.crud.list<ReservationResponse>('/api/reservations', { guestId: mine.id });
      }),
    );
  }

  /** The signed-in guest's own reviews — used to tell which stays they've already reviewed. */
  myReviews(): Observable<GuestReviewResponse[]> {
    const userId = this.auth.user()?.userId ?? 0;
    return this.crud.list<GuestProfileResponse>('/api/guests', { userId }).pipe(
      switchMap((profiles) => {
        const mine = profiles.find((p) => p.userId === userId) ?? profiles[0];
        if (!mine) {
          return of([] as GuestReviewResponse[]);
        }
        return this.crud.list<GuestReviewResponse>('/api/reviews', { guestId: mine.id });
      }),
    );
  }

  /**
   * Reservation ids that have a check-out record — i.e. stays that are actually
   * checked out. (A check-out doesn't change the reservation's own status, so
   * this is how we know a stay is complete and reviewable.)
   */
  checkedOutReservationIds(): Observable<number[]> {
    return this.crud
      .list<CheckOutRecordResponse>('/api/check-outs')
      .pipe(map((records) => records.map((r) => r.reservationId)));
  }

  /** Leave a review for a checked-out reservation. */
  submitReview(input: ReviewInput): Observable<GuestReviewResponse> {
    return this.crud.create<GuestReviewResponse>('/api/reviews', { ...input, status: 'PUBLISHED' });
  }

  /** The guest profile id for the signed-in user, creating one if needed. */
  private resolveGuestId(): Observable<number> {
    const user = this.auth.user();
    const userId = user?.userId ?? 0;
    return this.crud.list<GuestProfileResponse>('/api/guests', { userId }).pipe(
      switchMap((profiles) => {
        const mine = profiles.find((p) => p.userId === userId) ?? profiles[0];
        if (mine) {
          return of(mine.id);
        }
        return this.crud
          .create<GuestProfileResponse>('/api/guests', {
            userId,
            name: user?.email?.split('@')[0] ?? 'Guest',
            email: user?.email ?? '',
          })
          .pipe(map((p) => p.id));
      }),
    );
  }

  /** The occupied nights [checkIn, checkOut) as yyyy-MM-dd strings. */
  private nights(checkIn: string, checkOut: string): string[] {
    const res: string[] = [];
    if (!checkIn || !checkOut) {
      return res;
    }
    const end = new Date(checkOut + 'T00:00:00');
    for (const d = new Date(checkIn + 'T00:00:00'); d < end; d.setDate(d.getDate() + 1)) {
      res.push(`${d.getFullYear()}-${this.pad(d.getMonth() + 1)}-${this.pad(d.getDate())}`);
    }
    return res;
  }

  private pad(n: number): string {
    return String(n).padStart(2, '0');
  }
}
