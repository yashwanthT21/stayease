import { ResourceConfig } from '../../shared/crud/resource-config';
import { ACCESS_METHODS, CHECK_IN_STATUSES } from '../../core/models/enums';

// Reference helpers: point a dropdown at another resource's list for id labels.
const reservationRef = { resourceKey: 'reservations', labelFields: ['checkInDate', 'checkOutDate'] };
const guestRef = { resourceKey: 'guests', labelFields: ['name'] };

/**
 * Check-in screen (Stay domain) — mirrors the backend `stay` module's
 * CheckInRecord. There is no bespoke component: the shared
 * `ResourcePageComponent` renders this config into a full CRUD screen. It is
 * composed into the app-wide list in `core/registry.ts`.
 */
export const CHECK_IN_RESOURCE: ResourceConfig = {
  key: 'check-ins',
  apiBase: '/api/check-ins',
  title: 'Check-ins',
  singular: 'Check-in',
  icon: 'bi-box-arrow-in-right',
  group: 'Stay',
  roles: ['GUEST', 'PROPERTY_MANAGER'],
  listColumns: ['id', 'reservationId', 'guestId', 'actualCheckIn', 'accessMethod', 'welcomePackSent', 'status'],
  filters: [{ key: 'guestId', label: 'Guest', type: 'reference', ref: guestRef }],
  fields: [
    { key: 'reservationId', label: 'Reservation', type: 'reference', ref: reservationRef, required: true },
    { key: 'guestId', label: 'Guest', type: 'reference', ref: guestRef, required: true },
    { key: 'actualCheckIn', label: 'Actual check-in', type: 'datetime' },
    { key: 'accessMethod', label: 'Access method', type: 'select', options: ACCESS_METHODS },
    { key: 'welcomePackSent', label: 'Welcome pack sent', type: 'boolean' },
    { key: 'status', label: 'Status', type: 'select', options: CHECK_IN_STATUSES, help: 'Defaults to PENDING.' },
  ],
};
