import { ResourceConfig } from '../../../shared/crud/resource-config';
import { REVIEW_STATUSES } from '../../../core/models/enums';

// Reference helpers: point a dropdown at another resource's list for id labels.
const reservationRef = { resourceKey: 'reservations', labelFields: ['checkInDate', 'checkOutDate'] };
const guestRef = { resourceKey: 'guests', labelFields: ['name'] };

/**
 * Guest review screen (Stay domain) — mirrors the backend `stay` module's
 * GuestReview. This drives the generic list/CRUD screen via the shared
 * `ResourcePageComponent`; the bespoke owner analytics view lives alongside in
 * `review-analytics.ts`. Composed into the app-wide list in `core/registry.ts`.
 */
export const REVIEW_RESOURCE: ResourceConfig = {
  key: 'reviews',
  apiBase: '/api/reviews',
  title: 'Guest Reviews',
  singular: 'Review',
  icon: 'bi-star',
  group: 'Stay',
  // Reviews are written by guests; managers only view them here, and only for
  // reservations at their own properties.
  readOnlyRoles: ['PROPERTY_MANAGER'],
  managerScope: 'reservation',
  listColumns: ['id', 'reservationId', 'guestId', 'overallScore', 'cleanlinessScore', 'accuracyScore', 'locationScore', 'valueScore', 'status'],
  filters: [
    { key: 'reservationId', label: 'Reservation', type: 'reference', ref: reservationRef },
    { key: 'guestId', label: 'Guest', type: 'reference', ref: guestRef },
  ],
  fields: [
    { key: 'reservationId', label: 'Reservation', type: 'reference', ref: reservationRef, required: true },
    { key: 'guestId', label: 'Guest', type: 'reference', ref: guestRef, required: true },
    { key: 'cleanlinessScore', label: 'Cleanliness (1-5)', type: 'number', min: 1, max: 5 },
    { key: 'accuracyScore', label: 'Accuracy (1-5)', type: 'number', min: 1, max: 5 },
    { key: 'locationScore', label: 'Location (1-5)', type: 'number', min: 1, max: 5 },
    { key: 'valueScore', label: 'Value (1-5)', type: 'number', min: 1, max: 5 },
    { key: 'comments', label: 'Comments', type: 'textarea' },
    { key: 'status', label: 'Status', type: 'select', options: REVIEW_STATUSES, help: 'Defaults to PUBLISHED.' },
    { key: 'overallScore', label: 'Overall', type: 'number', hideInForm: true },
    { key: 'submittedDate', label: 'Submitted', type: 'datetime', hideInForm: true },
  ],
};
