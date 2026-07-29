import { Injectable, inject } from '@angular/core';
import { Observable, catchError, forkJoin, map, of, switchMap } from 'rxjs';
import { CrudService } from '../../../core/services/crud.service';
import { AuthService } from '../../../core/auth/auth.service';
import {
  AvailabilityCalendarResponse,
  GuestReviewResponse,
  OwnerPayoutResponse,
  OwnerStatementResponse,
  PropertyResponse,
  ReservationResponse,
  UserResponse,
} from '../../../core/models/dtos';

/** A property together with the reservations booked against it. */
export interface PropertyReservations {
  property: PropertyResponse;
  reservations: ReservationResponse[];
}

/**
 * Every owner screen loads the same underlying data (the signed-in owner's
 * properties and everything hanging off them). This service centralises those
 * calls — always scoped to the current owner's userId — so the individual
 * screens stay thin and never re-implement the fan-out logic.
 */
@Injectable({ providedIn: 'root' })
export class OwnerDataService {
  private crud = inject(CrudService);
  private auth = inject(AuthService);

  /** The signed-in owner's user id (0 when somehow signed out). */
  ownerId(): number {
    return this.auth.user()?.userId ?? 0;
  }

  /**
   * Only this owner's properties. If the signed-in owner id is somehow missing
   * we return nothing rather than call the endpoint unscoped — the properties
   * API falls back to returning EVERY owner's properties when ownerId is absent,
   * which must never happen on an owner screen.
   */
  myProperties(): Observable<PropertyResponse[]> {
    const ownerId = this.ownerId();
    if (!ownerId) {
      return of([]);
    }
    return this.crud.list<PropertyResponse>('/api/properties', { ownerId });
  }

  /** The property managers an owner can assign to a property. */
  managers(): Observable<UserResponse[]> {
    return this.crud.list<UserResponse>('/api/users/managers');
  }

  availability(propertyId: number): Observable<AvailabilityCalendarResponse[]> {
    return this.crud.list<AvailabilityCalendarResponse>('/api/availability', { propertyId });
  }

  reservationsForProperty(propertyId: number): Observable<ReservationResponse[]> {
    return this.crud.list<ReservationResponse>('/api/reservations', { propertyId });
  }

  reviewsForReservation(reservationId: number): Observable<GuestReviewResponse[]> {
    return this.crud.list<GuestReviewResponse>('/api/reviews', { reservationId });
  }

  /** Statements/payouts are owner-scoped (may 403 depending on backend RBAC). */
  myStatements(): Observable<OwnerStatementResponse[]> {
    const ownerId = this.ownerId();
    if (!ownerId) {
      return of([]);
    }
    return this.crud.list<OwnerStatementResponse>('/api/owner-statements', { ownerId });
  }

  myPayouts(): Observable<OwnerPayoutResponse[]> {
    const ownerId = this.ownerId();
    if (!ownerId) {
      return of([]);
    }
    return this.crud.list<OwnerPayoutResponse>('/api/owner-payouts', { ownerId });
  }

  /** Reservations for every one of the owner's properties, grouped by property. */
  myReservations(): Observable<PropertyReservations[]> {
    return this.myProperties().pipe(
      switchMap((props) => {
        if (!props.length) {
          return of([] as PropertyReservations[]);
        }
        return forkJoin(
          props.map((property) =>
            this.reservationsForProperty(property.id).pipe(
              map((reservations) => ({ property, reservations })),
              // One property failing must not blank the whole summary.
              catchError(() => of({ property, reservations: [] as ReservationResponse[] })),
            ),
          ),
        );
      }),
    );
  }

  /** Every review across all of the owner's properties (flattened). */
  myReviews(): Observable<GuestReviewResponse[]> {
    return this.myReservations().pipe(
      switchMap((groups) => {
        const ids = groups.flatMap((g) => g.reservations.map((r) => r.id));
        if (!ids.length) {
          return of([] as GuestReviewResponse[]);
        }
        return forkJoin(
          ids.map((id) =>
            // A single reservation's reviews failing must not blank the analytics.
            this.reviewsForReservation(id).pipe(catchError(() => of([] as GuestReviewResponse[]))),
          ),
        ).pipe(map((lists) => lists.flat()));
      }),
    );
  }
}
