import { ResourceConfig } from '../shared/crud/resource-config';
import {
  ACCESS_METHODS, AVAILABILITY_STATUSES, BOOKING_SOURCES, CHECK_IN_STATUSES,
  CHECK_OUT_STATUSES, CHECKLIST_CATEGORIES, CHECKLIST_STATUSES, GUEST_STATUSES, MAINTENANCE_CATEGORIES,
  MAINTENANCE_PRIORITIES, MAINTENANCE_STATUSES, NOTIFICATION_CATEGORIES, NOTIFICATION_STATUSES,
  PAYOUT_STATUSES, PREVENTIVE_FREQUENCIES, PREVENTIVE_STATUSES,
  PROPERTY_STATUSES, PROPERTY_TYPES, REPORTED_BY_TYPES, RESERVATION_STATUSES, REVIEW_STATUSES,
  STATEMENT_STATUSES, TURNOVER_STATUSES, USER_ROLES, USER_STATUSES, VERIFICATION_STATUSES,
} from './models/enums';

const propertyRef = { resourceKey: 'properties', labelFields: ['title', 'city'] };
const guestRef = { resourceKey: 'guests', labelFields: ['name'] };
const reservationRef = { resourceKey: 'reservations', labelFields: ['checkInDate', 'checkOutDate'] };
const turnoverRef = { resourceKey: 'turnovers', labelFields: ['status'] };
const statementRef = { resourceKey: 'owner-statements', labelFields: ['period'] };

/**
 * The single source of truth for the whole app. Every resource here becomes a
 * route, a nav item, and a full CRUD screen — driven by this metadata, not by
 * bespoke components. Roles mirror the backend's URL-based RBAC.
 */
export const RESOURCES: ResourceConfig[] = [
  // ============================ PROPERTY ============================
  {
    key: 'properties',
    apiBase: '/api/properties',
    title: 'Properties',
    singular: 'Property',
    icon: 'bi-houses',
    group: 'Property',
    // A property manager sees only the properties an owner assigned to them,
    // and cannot create/edit/delete listings — that's the owner's job.
    roleScope: { PROPERTY_MANAGER: { param: 'managerId', value: 'userId' } },
    readOnlyRoles: ['PROPERTY_MANAGER'],
    listColumns: ['id', 'title', 'type', 'city', 'maxGuests', 'bedrooms', 'bathrooms', 'status'],
    filters: [{ key: 'ownerId', label: 'Owner ID', type: 'number' }],
    fields: [
      { key: 'ownerId', label: 'Owner (user id)', type: 'number', required: true, min: 1, help: 'User ID of the owner.' },
      { key: 'managerId', label: 'Manager (user id)', type: 'number', min: 1 },
      { key: 'title', label: 'Title', type: 'text', required: true, maxLength: 200 },
      { key: 'type', label: 'Type', type: 'select', options: PROPERTY_TYPES, required: true },
      { key: 'city', label: 'City', type: 'text', required: true, maxLength: 120 },
      { key: 'maxGuests', label: 'Max guests', type: 'number', required: true, min: 1 },
      { key: 'bedrooms', label: 'Bedrooms', type: 'number', required: true, min: 0 },
      { key: 'bathrooms', label: 'Bathrooms', type: 'number', required: true, min: 0 },
      { key: 'amenitiesList', label: 'Amenities', type: 'textarea' },
      { key: 'houseRules', label: 'House rules', type: 'textarea' },
      { key: 'checkInTime', label: 'Check-in time', type: 'time' },
      { key: 'checkOutTime', label: 'Check-out time', type: 'time' },
      { key: 'status', label: 'Status', type: 'select', options: PROPERTY_STATUSES, help: 'Defaults to UNLISTED.' },
    ],
  },
  {
    // Route is special-cased to the shared AvailabilityCalendarComponent (see
    // app.routes.ts); this entry drives the property manager's sidebar item.
    // Owners reach the same component via their own nav (/owner/calendar).
    key: 'availability',
    apiBase: '/api/availability',
    title: 'Availability Calendar',
    singular: 'Availability entry',
    icon: 'bi-calendar3',
    group: 'Property',
    roles: ['PROPERTY_MANAGER'],
    listColumns: ['id', 'propertyId', 'calendarDate', 'availabilityStatus', 'basePrice', 'lastUpdated'],
    fields: [
      { key: 'propertyId', label: 'Property', type: 'reference', ref: propertyRef, required: true },
      { key: 'calendarDate', label: 'Date', type: 'date', required: true },
      { key: 'availabilityStatus', label: 'Availability', type: 'select', options: AVAILABILITY_STATUSES, help: 'Defaults to AVAILABLE.' },
      { key: 'basePrice', label: 'Price', type: 'money', required: true, min: 0.01 },
    ],
  },

  // ============================ BOOKING ============================
  {
    key: 'guests',
    apiBase: '/api/guests',
    title: 'Guests',
    singular: 'Guest profile',
    icon: 'bi-person-badge',
    group: 'Booking',
    // Managers may view guest profiles but not create/edit them.
    readOnlyRoles: ['PROPERTY_MANAGER'],
    listColumns: ['id', 'name', 'email', 'phone', 'nationality', 'verificationStatus', 'reviewScore', 'bookingCount', 'status'],
    filters: [{ key: 'userId', label: 'User ID', type: 'number' }],
    fields: [
      { key: 'userId', label: 'User (user id)', type: 'number', required: true, min: 1 },
      { key: 'name', label: 'Name', type: 'text', required: true, maxLength: 150 },
      { key: 'email', label: 'Email', type: 'email', required: true, maxLength: 180 },
      { key: 'phone', label: 'Phone', type: 'text', maxLength: 20 },
      { key: 'nationality', label: 'Nationality', type: 'text', maxLength: 80 },
      { key: 'verificationStatus', label: 'Verification', type: 'select', options: VERIFICATION_STATUSES, help: 'Defaults to UNVERIFIED.' },
      { key: 'status', label: 'Status', type: 'select', options: GUEST_STATUSES, help: 'Defaults to ACTIVE.' },
      { key: 'reviewScore', label: 'Review score', type: 'number', hideInForm: true },
      { key: 'bookingCount', label: 'Bookings', type: 'number', hideInForm: true },
    ],
  },
  {
    key: 'reservations',
    apiBase: '/api/reservations',
    title: 'Reservations',
    singular: 'Reservation',
    icon: 'bi-journal-check',
    group: 'Booking',
    // Managers view reservations (created by the customer booking flow) but do
    // not create them manually — they approve/reject pending requests instead.
    readOnlyRoles: ['PROPERTY_MANAGER'],
    patchActions: [
      { label: 'Approve', icon: 'bi-check2-circle', suffix: '/approve', showWhen: (r) => r['status'] === 'PENDING' },
      { label: 'Reject', icon: 'bi-x-circle', suffix: '/reject', showWhen: (r) => r['status'] === 'PENDING', variant: 'danger' },
    ],
    listColumns: ['id', 'propertyId', 'guestId', 'checkInDate', 'checkOutDate', 'nights', 'guestCount', 'totalAmount', 'status'],
    filters: [
      { key: 'propertyId', label: 'Property', type: 'reference', ref: propertyRef },
      { key: 'guestId', label: 'Guest', type: 'reference', ref: guestRef },
    ],
    fields: [
      { key: 'propertyId', label: 'Property', type: 'reference', ref: propertyRef, required: true },
      { key: 'guestId', label: 'Guest', type: 'reference', ref: guestRef, required: true },
      { key: 'checkInDate', label: 'Check-in date', type: 'date', required: true },
      { key: 'checkOutDate', label: 'Check-out date', type: 'date', required: true },
      { key: 'guestCount', label: 'Guests', type: 'number', required: true, min: 1 },
      { key: 'baseAmount', label: 'Base amount', type: 'money', required: true, min: 0.01 },
      { key: 'cleaningFee', label: 'Cleaning fee', type: 'money', min: 0 },
      { key: 'serviceFee', label: 'Service fee', type: 'money', min: 0 },
      { key: 'bookingSource', label: 'Source', type: 'select', options: BOOKING_SOURCES, help: 'Defaults to DIRECT.' },
      { key: 'status', label: 'Status', type: 'select', options: RESERVATION_STATUSES, help: 'Defaults to PENDING.' },
      { key: 'nights', label: 'Nights', type: 'number', hideInForm: true },
      { key: 'totalAmount', label: 'Total', type: 'money', hideInForm: true },
    ],
  },

  // ============================= STAY =============================
  {
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
  },
  {
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
  },
  {
    key: 'reviews',
    apiBase: '/api/reviews',
    title: 'Guest Reviews',
    singular: 'Review',
    icon: 'bi-star',
    group: 'Stay',
    // Reviews are written by guests; managers only view them here.
    readOnlyRoles: ['PROPERTY_MANAGER'],
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
  },

  // ========================= HOUSEKEEPING =========================
  {
    key: 'turnovers',
    apiBase: '/api/turnovers',
    title: 'Turnovers',
    singular: 'Turnover',
    icon: 'bi-arrow-repeat',
    group: 'Housekeeping',
    listColumns: ['id', 'propertyId', 'assignedToId', 'assignedDate', 'startByTime', 'completeByTime', 'status'],
    filters: [
      { key: 'propertyId', label: 'Property', type: 'reference', ref: propertyRef },
      { key: 'assignedToId', label: 'Assigned To (user id)', type: 'number' },
    ],
    fields: [
      { key: 'propertyId', label: 'Property', type: 'reference', ref: propertyRef, required: true },
      { key: 'checkOutReservationId', label: 'Check-out reservation', type: 'reference', ref: reservationRef },
      { key: 'checkInReservationId', label: 'Check-in reservation', type: 'reference', ref: reservationRef },
      { key: 'assignedToId', label: 'Assigned to (user id)', type: 'number', min: 1 },
      { key: 'assignedDate', label: 'Assigned date', type: 'date' },
      { key: 'startByTime', label: 'Start by', type: 'datetime' },
      { key: 'completeByTime', label: 'Complete by', type: 'datetime' },
      { key: 'status', label: 'Status', type: 'select', options: TURNOVER_STATUSES, help: 'Defaults to PENDING.' },
    ],
  },
  {
    key: 'checklists',
    apiBase: '/api/checklists',
    title: 'Turnover Checklists',
    singular: 'Checklist item',
    icon: 'bi-check2-square',
    group: 'Housekeeping',
    // Checklists are created/managed by housekeepers; managers only view them.
    readOnlyRoles: ['PROPERTY_MANAGER'],
    listColumns: ['id', 'turnoverId', 'taskName', 'category', 'completed', 'status'],
    filters: [{ key: 'turnoverId', label: 'Turnover', type: 'reference', ref: turnoverRef, required: true }],
    fields: [
      { key: 'turnoverId', label: 'Turnover', type: 'reference', ref: turnoverRef, required: true },
      { key: 'taskName', label: 'Task name', type: 'text', required: true, maxLength: 150 },
      { key: 'category', label: 'Category', type: 'select', options: CHECKLIST_CATEGORIES, required: true },
      { key: 'completed', label: 'Completed', type: 'boolean' },
      { key: 'notes', label: 'Notes', type: 'textarea' },
      { key: 'status', label: 'Status', type: 'select', options: CHECKLIST_STATUSES, help: 'Defaults to PENDING.' },
    ],
  },

  // ========================== MAINTENANCE ==========================
  {
    key: 'maintenance-issues',
    apiBase: '/api/maintenance-issues',
    title: 'Maintenance Issues',
    singular: 'Issue',
    icon: 'bi-tools',
    group: 'Maintenance',
    listColumns: ['id', 'propertyId', 'category', 'priority', 'reportedByType', 'reportedDate', 'status'],
    filters: [
      { key: 'propertyId', label: 'Property', type: 'reference', ref: propertyRef },
      { key: 'status', label: 'Status', type: 'select', options: MAINTENANCE_STATUSES },
    ],
    fields: [
      { key: 'propertyId', label: 'Property', type: 'reference', ref: propertyRef, required: true },
      { key: 'reportedById', label: 'Reported by (user id)', type: 'number', required: true, min: 1 },
      { key: 'reportedByType', label: 'Reporter type', type: 'select', options: REPORTED_BY_TYPES, required: true },
      { key: 'category', label: 'Category', type: 'select', options: MAINTENANCE_CATEGORIES, required: true },
      { key: 'description', label: 'Description', type: 'textarea' },
      { key: 'priority', label: 'Priority', type: 'select', options: MAINTENANCE_PRIORITIES, help: 'Defaults to MEDIUM.' },
      { key: 'assignedContractorId', label: 'Contractor (user id)', type: 'number', min: 1 },
      { key: 'resolvedDate', label: 'Resolved date', type: 'datetime' },
      { key: 'status', label: 'Status', type: 'select', options: MAINTENANCE_STATUSES, help: 'Defaults to OPEN.' },
      { key: 'reportedDate', label: 'Reported', type: 'datetime', hideInForm: true },
    ],
  },
  {
    key: 'preventive-maintenance',
    apiBase: '/api/preventive-maintenance',
    title: 'Preventive Maintenance',
    singular: 'Preventive task',
    icon: 'bi-clipboard-check',
    group: 'Maintenance',
    listColumns: ['id', 'propertyId', 'taskName', 'frequency', 'nextScheduledDate', 'lastCompletedDate', 'status'],
    filters: [{ key: 'propertyId', label: 'Property', type: 'reference', ref: propertyRef }],
    fields: [
      { key: 'propertyId', label: 'Property', type: 'reference', ref: propertyRef, required: true },
      { key: 'taskName', label: 'Task name', type: 'text', required: true, maxLength: 150 },
      { key: 'frequency', label: 'Frequency', type: 'select', options: PREVENTIVE_FREQUENCIES, required: true },
      { key: 'nextScheduledDate', label: 'Next scheduled', type: 'date' },
      { key: 'lastCompletedDate', label: 'Last completed', type: 'date' },
      { key: 'status', label: 'Status', type: 'select', options: PREVENTIVE_STATUSES, help: 'Defaults to SCHEDULED.' },
    ],
  },

  // ============================ FINANCE ============================
  {
    key: 'owner-statements',
    apiBase: '/api/owner-statements',
    title: 'Owner Statements',
    singular: 'Statement',
    icon: 'bi-file-earmark-text',
    group: 'Finance',
    roles: ['ADMIN', 'FINANCE'],
    listColumns: ['id', 'ownerId', 'period', 'grossRevenue', 'netPayout', 'status'],
    filters: [{ key: 'ownerId', label: 'Owner ID', type: 'number' }],
    fields: [
      { key: 'ownerId', label: 'Owner (user id)', type: 'number', required: true, min: 1 },
      { key: 'period', label: 'Period', type: 'text', required: true, maxLength: 20, placeholder: '2026-06' },
      { key: 'grossRevenue', label: 'Gross revenue', type: 'money', min: 0 },
      { key: 'platformFee', label: 'Platform fee', type: 'money', min: 0 },
      { key: 'managementFee', label: 'Management fee', type: 'money', min: 0 },
      { key: 'cleaningRevenue', label: 'Cleaning revenue', type: 'money', min: 0 },
      { key: 'maintenanceCost', label: 'Maintenance cost', type: 'money', min: 0 },
      { key: 'status', label: 'Status', type: 'select', options: STATEMENT_STATUSES, help: 'Defaults to DRAFT.' },
      { key: 'netPayout', label: 'Net payout', type: 'money', hideInForm: true },
      { key: 'generatedDate', label: 'Generated', type: 'datetime', hideInForm: true },
    ],
  },
  {
    key: 'owner-payouts',
    apiBase: '/api/owner-payouts',
    title: 'Owner Payouts',
    singular: 'Payout',
    icon: 'bi-cash-coin',
    group: 'Finance',
    roles: ['ADMIN', 'FINANCE'],
    listColumns: ['id', 'statementId', 'ownerId', 'amount', 'paymentDate', 'status'],
    filters: [
      { key: 'ownerId', label: 'Owner ID', type: 'number' },
      { key: 'statementId', label: 'Statement ID', type: 'number' },
    ],
    fields: [
      { key: 'statementId', label: 'Statement', type: 'reference', ref: statementRef, required: true },
      { key: 'ownerId', label: 'Owner (user id)', type: 'number', required: true, min: 1 },
      { key: 'amount', label: 'Amount', type: 'money', required: true, min: 0.01 },
      { key: 'paymentDate', label: 'Payment date', type: 'date' },
      { key: 'bankAccountRef', label: 'Bank account ref', type: 'text', maxLength: 120 },
      { key: 'status', label: 'Status', type: 'select', options: PAYOUT_STATUSES, help: 'Defaults to PENDING.' },
    ],
  },

  // ========================= NOTIFICATIONS =========================
  {
    key: 'notifications',
    apiBase: '/api/notifications',
    title: 'Notifications',
    singular: 'Notification',
    icon: 'bi-bell',
    group: 'Notifications',
    // Guests see only their own notifications and can't author them (but can
    // still mark read / dismiss via the patch actions below).
    roleScope: { GUEST: { param: 'userId', value: 'userId' } },
    readOnlyRoles: ['GUEST'],
    listColumns: ['id', 'userId', 'message', 'category', 'status', 'createdDate'],
    filters: [
      { key: 'userId', label: 'User ID', type: 'number' },
      { key: 'status', label: 'Status', type: 'select', options: NOTIFICATION_STATUSES },
    ],
    patchActions: [
      { label: 'Mark read', icon: 'bi-check2', suffix: '/read', showWhen: (r) => r['status'] !== 'READ' },
      { label: 'Dismiss', icon: 'bi-x-circle', suffix: '/dismiss', showWhen: (r) => r['status'] !== 'DISMISSED' },
    ],
    fields: [
      { key: 'userId', label: 'User (user id)', type: 'number', required: true, min: 1 },
      { key: 'message', label: 'Message', type: 'textarea', required: true, maxLength: 500 },
      { key: 'category', label: 'Category', type: 'select', options: NOTIFICATION_CATEGORIES, required: true },
      { key: 'status', label: 'Status', type: 'select', options: NOTIFICATION_STATUSES, help: 'Defaults to UNREAD.' },
      { key: 'createdDate', label: 'Created', type: 'datetime', hideInForm: true },
    ],
  },

  // ============================= ADMIN =============================
  {
    key: 'users',
    apiBase: '/api/users',
    title: 'Users',
    singular: 'User',
    icon: 'bi-people',
    group: 'Administration',
    roles: ['ADMIN'],
    listColumns: ['id', 'name', 'email', 'phone', 'role', 'status'],
    fields: [
      { key: 'name', label: 'Name', type: 'text', required: true, maxLength: 150 },
      { key: 'email', label: 'Email', type: 'email', required: true, maxLength: 180 },
      { key: 'phone', label: 'Phone', type: 'text', maxLength: 20 },
      { key: 'role', label: 'Role', type: 'select', options: USER_ROLES, required: true },
      { key: 'status', label: 'Status', type: 'select', options: USER_STATUSES, help: 'Defaults to ACTIVE.' },
    ],
  },
  {
    key: 'audit-logs',
    apiBase: '/api/audit-logs',
    title: 'Audit Logs',
    singular: 'Audit log',
    icon: 'bi-list-columns-reverse',
    group: 'Administration',
    roles: ['ADMIN', 'FINANCE'],
    readOnly: true,
    listColumns: ['id', 'userId', 'action', 'entityType', 'loggedAt'],
    filters: [
      { key: 'userId', label: 'User ID', type: 'number' },
      { key: 'entityType', label: 'Entity type', type: 'text' },
    ],
    fields: [
      { key: 'userId', label: 'User ID', type: 'number', hideInForm: true },
      { key: 'action', label: 'Action', type: 'text', hideInForm: true },
      { key: 'entityType', label: 'Entity type', type: 'text', hideInForm: true },
      { key: 'loggedAt', label: 'Logged at', type: 'datetime', hideInForm: true },
    ],
  },
];

const BY_KEY = new Map<string, ResourceConfig>(RESOURCES.map((r) => [r.key, r]));

export function getResource(key: string): ResourceConfig | undefined {
  return BY_KEY.get(key);
}

/** Nav group display order. */
export const NAV_GROUP_ORDER = ['Property', 'Booking', 'Stay', 'Housekeeping', 'Maintenance', 'Finance', 'Notifications', 'Administration'];
