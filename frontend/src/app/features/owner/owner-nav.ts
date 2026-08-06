/**
 * Owner-only navigation metadata. Kept separate from the generic RESOURCES
 * registry so the bespoke owner experience never leaks into the other roles'
 * navigation.
 */
export interface OwnerNavItem {
  /** Absolute router path. */
  path: string;
  title: string;
  /** bootstrap-icons class, e.g. 'bi-houses'. */
  icon: string;
  /** One-line description shown on the owner home hub cards. */
  desc: string;
}

/**
 * The owner modules, in the order the brief lists them, plus the shared
 * notifications inbox — an owner is notified when a guest reviews one of their
 * properties, so they need somewhere to read it.
 */
export const OWNER_NAV: OwnerNavItem[] = [
  { path: '/owner/listings', title: 'Property Listing', icon: 'bi-building', desc: 'Browse how each of your properties appears to guests.' },
  { path: '/owner/properties', title: 'Listing Manager', icon: 'bi-houses', desc: 'Add, edit, publish and unpublish your properties.' },
  { path: '/owner/calendar', title: 'Availability Calendar', icon: 'bi-calendar3', desc: 'View and manage nightly availability and pricing.' },
  { path: '/owner/bookings', title: 'Booking Summary', icon: 'bi-journal-check', desc: 'Track reservations, revenue and upcoming stays.' },
  { path: '/owner/payouts', title: 'Payout Statement', icon: 'bi-cash-coin', desc: 'Review your monthly statements and payouts.' },
  { path: '/owner/reviews', title: 'Review Analytics', icon: 'bi-star', desc: 'Understand guest satisfaction across your portfolio.' },
  { path: '/notifications', title: 'Notifications', icon: 'bi-bell', desc: 'New reviews, listings and other updates on your properties.' },
];
