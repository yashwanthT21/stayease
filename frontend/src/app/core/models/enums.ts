/**
 * Enum values mirrored exactly from the backend (uppercase string names).
 * Each is a runtime `as const` array (used to build <select> options) plus a
 * derived string-literal type used across the models.
 */

// ---- IAM ----
export const USER_ROLES = ['OWNER', 'GUEST', 'PROPERTY_MANAGER', 'HOUSEKEEPING', 'FINANCE', 'ADMIN'] as const;
export type UserRole = (typeof USER_ROLES)[number];

export const USER_STATUSES = ['ACTIVE', 'SUSPENDED', 'INACTIVE'] as const;
export type UserStatus = (typeof USER_STATUSES)[number];

// ---- Booking ----
export const BOOKING_SOURCES = ['DIRECT', 'PLATFORM', 'AGENT'] as const;
export type BookingSource = (typeof BOOKING_SOURCES)[number];

export const GUEST_STATUSES = ['ACTIVE', 'BLACKLISTED'] as const;
export type GuestStatus = (typeof GUEST_STATUSES)[number];

export const RESERVATION_STATUSES = ['PENDING', 'CONFIRMED', 'ACTIVE', 'CHECKED_OUT', 'CANCELLED', 'NO_SHOW'] as const;
export type ReservationStatus = (typeof RESERVATION_STATUSES)[number];

export const VERIFICATION_STATUSES = ['UNVERIFIED', 'ID_VERIFIED', 'TRUSTED'] as const;
export type VerificationStatus = (typeof VERIFICATION_STATUSES)[number];

// ---- Stay ----
export const ACCESS_METHODS = ['KEY_COLLECTION', 'SMART_LOCK', 'KEYSAFE'] as const;
export type AccessMethod = (typeof ACCESS_METHODS)[number];

export const CHECK_IN_STATUSES = ['PENDING', 'CHECKED_IN', 'NO_SHOW'] as const;
export type CheckInStatus = (typeof CHECK_IN_STATUSES)[number];

export const CHECK_OUT_STATUSES = ['CHECKED_OUT', 'DAMAGE_REPORTED'] as const;
export type CheckOutStatus = (typeof CHECK_OUT_STATUSES)[number];

/**
 * What a check-out form actually OFFERS — only CHECKED_OUT.
 *
 * DAMAGE_REPORTED was a second way to say the same thing: the screen already has
 * a dedicated "Damage noted" flag plus a description, so recording damage in the
 * status as well let one record disagree with itself (status DAMAGE_REPORTED,
 * damageNoted false). Recording a departure now has exactly one outcome, and
 * damage is captured by the flag.
 *
 * The value stays in CHECK_OUT_STATUSES (and in the backend enum) on purpose, so
 * rows already saved with it still read and badge correctly — it just can't be
 * chosen again.
 */
export const CHECK_OUT_STATUS_OPTIONS = ['CHECKED_OUT'] as const;

export const REVIEW_STATUSES = ['PUBLISHED', 'MODERATED', 'REMOVED'] as const;
export type ReviewStatus = (typeof REVIEW_STATUSES)[number];

// ---- Housekeeping ----
export const CHECKLIST_CATEGORIES = ['CLEANING', 'LAUNDRY', 'RESTOCKING', 'INSPECTION', 'PHOTOGRAPHY'] as const;
export type ChecklistCategory = (typeof CHECKLIST_CATEGORIES)[number];

export const CHECKLIST_STATUSES = ['PENDING', 'DONE'] as const;
export type ChecklistStatus = (typeof CHECKLIST_STATUSES)[number];

export const TURNOVER_STATUSES = ['PENDING', 'IN_PROGRESS', 'COMPLETED', 'ISSUE_REPORTED'] as const;
export type TurnoverStatus = (typeof TURNOVER_STATUSES)[number];

// Housekeeper's own completion state on a turnover (separate from the manager status).
export const HOUSEKEEPER_STATUSES = ['PENDING', 'COMPLETED'] as const;
export type HousekeeperStatus = (typeof HOUSEKEEPER_STATUSES)[number];

// ---- Maintenance ----
export const MAINTENANCE_CATEGORIES = ['PLUMBING', 'ELECTRICAL', 'APPLIANCE', 'STRUCTURAL', 'PEST', 'OTHER'] as const;
export type MaintenanceCategory = (typeof MAINTENANCE_CATEGORIES)[number];

export const MAINTENANCE_PRIORITIES = ['LOW', 'MEDIUM', 'HIGH', 'EMERGENCY'] as const;
export type MaintenancePriority = (typeof MAINTENANCE_PRIORITIES)[number];

export const MAINTENANCE_STATUSES = ['OPEN', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'] as const;
export type MaintenanceStatus = (typeof MAINTENANCE_STATUSES)[number];

export const PREVENTIVE_FREQUENCIES = ['MONTHLY', 'QUARTERLY', 'ANNUAL'] as const;
export type PreventiveFrequency = (typeof PREVENTIVE_FREQUENCIES)[number];

export const PREVENTIVE_STATUSES = ['SCHEDULED', 'COMPLETED', 'OVERDUE'] as const;
export type PreventiveStatus = (typeof PREVENTIVE_STATUSES)[number];

export const REPORTED_BY_TYPES = ['GUEST', 'HOUSEKEEPING', 'MANAGER', 'OWNER'] as const;
export type ReportedByType = (typeof REPORTED_BY_TYPES)[number];

// ---- Finance ----
export const PAYOUT_STATUSES = ['PENDING', 'PAID', 'FAILED'] as const;
export type PayoutStatus = (typeof PAYOUT_STATUSES)[number];

export const STATEMENT_STATUSES = ['DRAFT', 'ISSUED', 'APPROVED', 'REJECTED', 'PAID'] as const;
export type StatementStatus = (typeof STATEMENT_STATUSES)[number];

/**
 * The statuses FINANCE may set on a statement. APPROVED and REJECTED are the
 * owner's answer, set only through their approve/reject actions — offering them in
 * Finance's dropdown would let Finance approve on the owner's behalf and walk
 * straight through the payout gate.
 */
export const STATEMENT_STATUS_OPTIONS = ['DRAFT', 'ISSUED', 'PAID'] as const;

/** A payout may only be created against a statement in this state. */
export const STATEMENT_APPROVED: StatementStatus = 'APPROVED';

// ---- Property ----
export const PROPERTY_TYPES = ['APARTMENT', 'VILLA', 'COTTAGE', 'TOWNHOUSE', 'STUDIO', 'BUNGALOW_ROOM'] as const;
export type PropertyType = (typeof PROPERTY_TYPES)[number];

export const PROPERTY_STATUSES = ['LISTED', 'UNLISTED', 'UNDER_MAINTENANCE'] as const;
export type PropertyStatus = (typeof PROPERTY_STATUSES)[number];

export const AVAILABILITY_STATUSES = ['AVAILABLE', 'BOOKED', 'BLOCKED', 'OWNER_USE'] as const;
export type AvailabilityStatus = (typeof AVAILABILITY_STATUSES)[number];

// Pricing-rule enums were removed with the pricing-rule API (see
// property-service): the PricingRule entity survives only so deleting a property
// can clean up its old rows, and nothing in the client ever reads them.

// ---- Notification ----
export const NOTIFICATION_CATEGORIES = ['BOOKING', 'CHECK_IN', 'HOUSEKEEPING', 'MAINTENANCE', 'PAYOUT', 'REVIEW', 'PROPERTY'] as const;
export type NotificationCategory = (typeof NOTIFICATION_CATEGORIES)[number];

export const NOTIFICATION_STATUSES = ['UNREAD', 'READ', 'DISMISSED'] as const;
export type NotificationStatus = (typeof NOTIFICATION_STATUSES)[number];
