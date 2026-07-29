import { Routes } from '@angular/router';
import { roleGuard } from '../../core/guards/role.guard';
import { OwnerHomeComponent } from './pages/home/owner-home';
import { PropertyListingComponent } from './pages/listings/property-listing';
import { PropertyManagerComponent } from './pages/properties/property-manager';
import { AvailabilityCalendarComponent } from './pages/calendar/availability-calendar';
import { BookingSummaryComponent } from './pages/bookings/booking-summary';
import { PayoutStatementComponent } from './pages/payouts/payout-statement';
import { ReviewAnalyticsComponent } from './pages/reviews/review-analytics';

/**
 * The owner-only area. The whole subtree is gated to the OWNER role so no other
 * role can reach these bespoke screens, and none of the generic resource
 * routes are affected.
 */
export const OWNER_ROUTES: Routes = [
  {
    path: 'owner',
    canActivate: [roleGuard],
    data: { roles: ['OWNER'] },
    children: [
      { path: '', component: OwnerHomeComponent },
      { path: 'listings', component: PropertyListingComponent },
      { path: 'properties', component: PropertyManagerComponent },
      { path: 'calendar', component: AvailabilityCalendarComponent },
      { path: 'bookings', component: BookingSummaryComponent },
      { path: 'payouts', component: PayoutStatementComponent },
      { path: 'reviews', component: ReviewAnalyticsComponent },
    ],
  },
];
