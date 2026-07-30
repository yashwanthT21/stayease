import { ResourceConfig } from '../../shared/crud/resource-config';
import { CHECK_OUT_STATUSES } from '../../core/models/enums';

// Reference helper: point a dropdown at another resource's list for id labels.
const reservationRef = { resourceKey: 'reservations', labelFields: ['checkInDate', 'checkOutDate'] };

/**
 * Check-out screen (Stay domain) — mirrors the backend `stay` module's
 * CheckOutRecord. There is no bespoke component: the shared
 * `ResourcePageComponent` renders this config into a full CRUD screen. It is
 * composed into the app-wide list in `core/registry.ts`.
 */
export const CHECK_OUT_RESOURCE: ResourceConfig = {
  key: 'check-outs',
  apiBase: '/api/check-outs',
  title: 'Check-outs',
  singular: 'Check-out',
  icon: 'bi-box-arrow-right',
  group: 'Stay',
  listColumns: ['id', 'reservationId', 'actualCheckOut', 'damageNoted', 'depositReleased', 'status'],
  fields: [
    { key: 'reservationId', label: 'Reservation', type: 'reference', ref: reservationRef, required: true },
    { key: 'actualCheckOut', label: 'Actual check-out', type: 'datetime' },
    { key: 'damageNoted', label: 'Damage noted', type: 'boolean' },
    { key: 'damageDescription', label: 'Damage description', type: 'textarea' },
    { key: 'depositReleased', label: 'Deposit released', type: 'boolean' },
    { key: 'status', label: 'Status', type: 'select', options: CHECK_OUT_STATUSES, help: 'Defaults to CHECKED_OUT.' },
  ],
};
