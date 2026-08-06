import { Routes } from '@angular/router';
import { roleGuard } from '../../core/guards/role.guard';
import { OwnerHomeComponent } from './owner-home';
import { PropertyListingComponent } from '../property/property-listing/property-listing';
import { PropertyManagerComponent } from '../property/property-manager/property-manager';
import { AvailabilityCalendarComponent } from '../property/availability-calendar/availability-calendar';
import { BookingSummaryComponent } from '../booking/booking-summary/booking-summary';
import { PayoutStatementComponent } from '../finance/payout-statement/payout-statement';
import { ReviewAnalyticsComponent } from '../stay/review-analytics/review-analytics';

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
