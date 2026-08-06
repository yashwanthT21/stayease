/**
 * Request/response shapes mirrored from the backend DTOs.
 * All money fields are strings/numbers (backend BigDecimal) — never do float
 * math on them in the client. Dates are ISO strings ("yyyy-MM-dd" or full
 * ISO date-time) as serialized by Jackson.
 */
import {
  AccessMethod, AvailabilityStatus, BookingSource, CheckInStatus, CheckOutStatus,
  ChecklistCategory, ChecklistStatus, GuestStatus, HousekeeperStatus, MaintenanceCategory, MaintenancePriority,
  MaintenanceStatus, NotificationCategory, NotificationStatus, PayoutStatus, PreventiveFrequency,
  PreventiveStatus, PropertyStatus, PropertyType, ReportedByType,
  ReservationStatus, ReviewStatus, StatementStatus, TurnoverStatus, UserRole, UserStatus, VerificationStatus,
} from './enums';

/** Every response carries a numeric id. */
export interface HasId {
  id: number;
}

// ---- IAM ----
export interface UserResponse extends HasId {
  name: string;
  email: string;
  phone?: string;
  role: UserRole;
  status: UserStatus;
}

export interface AuditLogResponse extends HasId {
  userId: number;
  action: string;
  entityType: string;
  loggedAt: string;
}

// ---- Booking ----
export interface GuestProfileResponse extends HasId {
  userId: number;
  name: string;
  email: string;
  phone?: string;
  nationality?: string;
  verificationStatus: VerificationStatus;
  reviewScore?: number;
  bookingCount: number;
  status: GuestStatus;
}

export interface ReservationResponse extends HasId {
  propertyId: number;
  guestId: number;
  checkInDate: string;
  checkOutDate: string;
  nights: number;
  guestCount: number;
  baseAmount: number;
  cleaningFee?: number;
  serviceFee?: number;
  totalAmount: number;
  bookingSource: BookingSource;
  status: ReservationStatus;
}

// ---- Stay ----
export interface CheckInRecordResponse extends HasId {
  reservationId: number;
  guestId: number;
  actualCheckIn?: string;
  accessMethod?: AccessMethod;
  welcomePackSent: boolean;
  status: CheckInStatus;
}

export interface CheckOutRecordResponse extends HasId {
  reservationId: number;
  actualCheckOut?: string;
  damageNoted: boolean;
  damageDescription?: string;
  depositReleased: boolean;
  status: CheckOutStatus;
}

export interface GuestReviewResponse extends HasId {
  reservationId: number;
  guestId: number;
  cleanlinessScore?: number;
  accuracyScore?: number;
  locationScore?: number;
  valueScore?: number;
  overallScore?: number;
  comments?: string;
  submittedDate?: string;
  status: ReviewStatus;
}

// ---- Housekeeping ----
export interface TurnoverAssignmentResponse extends HasId {
  propertyId: number;
  checkOutReservationId?: number;
  checkInReservationId?: number;
  assignedToId?: number;
  assignedDate?: string;
  startByTime?: string;
  completeByTime?: string;
  status: TurnoverStatus;
  housekeeperStatus: HousekeeperStatus;
}

export interface TurnoverChecklistResponse extends HasId {
  turnoverId: number;
  taskName: string;
  category: ChecklistCategory;
  completed: boolean;
  notes?: string;
  status: ChecklistStatus;
}

// ---- Maintenance ----
export interface MaintenanceIssueResponse extends HasId {
  propertyId: number;
  reportedById: number;
  reportedByType: ReportedByType;
  category: MaintenanceCategory;
  description?: string;
  priority: MaintenancePriority;
  assignedContractorId?: number;
  reportedDate?: string;
  resolvedDate?: string;
  amountSpent?: number;
  status: MaintenanceStatus;
}

export interface PreventiveMaintenanceResponse extends HasId {
  propertyId: number;
  taskName: string;
  frequency: PreventiveFrequency;
  nextScheduledDate?: string;
  lastCompletedDate?: string;
  status: PreventiveStatus;
}

// ---- Finance ----
export interface OwnerPayoutResponse extends HasId {
  statementId: number;
  ownerId: number;
  amount: number;
  paymentDate?: string;
  bankAccountRef?: string;
  status: PayoutStatus;
}

export interface OwnerStatementResponse extends HasId {
  ownerId: number;
  period: string;
  grossRevenue?: number;
  platformFee?: number;
  managementFee?: number;
  cleaningRevenue?: number;
  maintenanceCost?: number;
  netPayout?: number;
  generatedDate?: string;
  status: StatementStatus;
  /** The owner's approve/reject comment — usually why they rejected it. */
  ownerNote?: string;
  /** When the owner answered; absent while the statement still awaits them. */
  decidedDate?: string;
}

// ---- Property ----
export interface PropertyResponse extends HasId {
  ownerId: number;
  managerId?: number;
  title: string;
  type: PropertyType;
  city: string;
  maxGuests: number;
  bedrooms: number;
  bathrooms: number;
  amenitiesList?: string;
  houseRules?: string;
  checkInTime?: string;
  checkOutTime?: string;
  status: PropertyStatus;
}

export interface AvailabilityCalendarResponse extends HasId {
  propertyId: number;
  calendarDate: string;
  availabilityStatus: AvailabilityStatus;
  basePrice: number;
  minimumNights: number;
  lastUpdated?: string;
}

// ---- Notification ----
export interface NotificationResponse extends HasId {
  userId: number;
  message: string;
  category: NotificationCategory;
  status: NotificationStatus;
  createdDate?: string;
}

/** Standard backend error body (GlobalExceptionHandler / ErrorResponse). */
export interface ApiError {
  status?: number;
  error?: string;
  message?: string;
  timestamp?: string;
  path?: string;
  fieldErrors?: Record<string, string>;
}
