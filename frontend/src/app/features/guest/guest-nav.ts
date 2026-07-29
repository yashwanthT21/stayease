/**
 * Guest-only sidebar. A customer only needs to browse & book properties, track
 * their own reservations, and read notifications — so the generic operational
 * tabs are not shown to them.
 */
export interface GuestNavItem {
  path: string;
  title: string;
  icon: string;
}

export const GUEST_NAV: GuestNavItem[] = [
  { path: '/guest/browse', title: 'Properties', icon: 'bi-houses' },
  { path: '/guest/reservations', title: 'My Reservations', icon: 'bi-journal-check' },
  { path: '/guest/profile', title: 'My Profile', icon: 'bi-person-circle' },
  { path: '/notifications', title: 'Notifications', icon: 'bi-bell' },
];
