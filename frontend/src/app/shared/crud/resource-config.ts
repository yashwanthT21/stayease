import { UserRole } from '../../core/models/enums';

export type FieldType =
  | 'text'
  | 'textarea'
  | 'email'
  | 'number'
  | 'money'
  | 'date'
  | 'datetime'
  | 'time'
  | 'boolean'
  | 'select'
  | 'reference';

/** Points a reference field/filter at another resource for a dropdown of ids. */
export interface RefConfig {
  /** Key of the resource in the registry whose list populates the options. */
  resourceKey: string;
  /** Fields from that resource joined to form each option's label. */
  labelFields: string[];
}

export interface FieldConfig {
  key: string;
  label: string;
  type: FieldType;
  options?: readonly string[]; // enum values for 'select'
  /**
   * Friendlier table/detail labels for raw enum values, when several values mean
   * the same thing to the reader — e.g. a guest's ID_VERIFIED and TRUSTED both
   * read as "Verified". Only affects display; the stored value is untouched.
   */
  valueLabels?: Readonly<Record<string, string>>;
  ref?: RefConfig; // for 'reference'
  required?: boolean;
  min?: number;
  max?: number;
  step?: number;
  maxLength?: number;
  placeholder?: string;
  help?: string;
  /** Server-computed fields: shown in the table/detail but not the form. */
  hideInForm?: boolean;
  hideInList?: boolean;
}

export interface FilterConfig {
  key: string;
  label: string;
  type: 'number' | 'text' | 'select' | 'reference';
  options?: readonly string[];
  ref?: RefConfig;
  /** The list will not load until this filter has a value (backend 400s otherwise). */
  required?: boolean;
}

/**
 * A value picker rendered inside a table column's own header, filtering the
 * loaded rows down to the chosen value. Unlike FilterConfig (which is a query
 * param sent to the backend) this is purely client-side and matches against the
 * column's DISPLAYED text — so it works with `valueLabels` collapsing several
 * enum values into one reader-facing option.
 */
export interface ColumnFilterConfig {
  /** A key from `listColumns`. */
  key: string;
  /** Selectable displayed values; '' ("All") is added automatically. */
  options: readonly string[];
}

/**
 * Turns a table column into an editable per-row dropdown.
 *
 * Different from ColumnFilterConfig, which narrows the list: this one CHANGES the
 * record. The header keeps just its label, and each cell becomes a select that
 * saves the row on pick — used for the manager's Guests → Verification column,
 * where the answer is a two-way switch a manager should be able to flip in place
 * rather than open an edit modal for.
 *
 * The two mapping functions exist because the reader-facing options need not be
 * the stored values: verification is stored as a three-value tier
 * (UNVERIFIED / ID_VERIFIED / TRUSTED) but presented as Verified / Unverified.
 */
export interface RowEditorConfig {
  /** A key from `listColumns`. */
  key: string;
  /** The options offered, in display order. The FIRST is the default shown when
   *  the row's stored value maps to nothing. */
  options: readonly string[];
  /** Row → which option is currently selected. */
  fromRow: (row: Record<string, unknown>) => string;
  /** Chosen option → the value to persist in `key`. */
  toValue: (option: string) => string;
  /** Roles allowed to change it; omitted = every role that can see the screen.
   *  Needed because Guests is otherwise read-only for a PROPERTY_MANAGER. */
  roles?: readonly UserRole[];
}

/** An extra PATCH transition offered per row (notifications read/dismiss). */
export interface PatchAction {
  label: string;
  icon: string;
  /** Appended to `${apiBase}/${id}` — e.g. '/read'. */
  suffix: string;
  /** Only show the button when this predicate holds for the row. */
  showWhen?: (row: Record<string, unknown>) => boolean;
  /** Bootstrap outline colour suffix for the button (default 'success'). */
  variant?: 'success' | 'danger' | 'primary' | 'secondary' | 'warning';
}

/**
 * Auto-applied, per-role list scoping. When the signed-in user has a listed
 * role, the given query param is forced to their own user id on every list
 * request — so, e.g., a PROPERTY_MANAGER only sees the properties assigned to
 * them. Roles not listed here are unaffected (admins still see everything).
 */
export type RoleScope = Partial<Record<UserRole, { param: string; value: 'userId' }>>;

export interface ResourceConfig {
  key: string; // route segment, e.g. 'reservations'
  apiBase: string; // '/api/reservations'
  title: string; // 'Reservations'
  singular: string; // 'Reservation'
  icon: string; // bootstrap-icons class, e.g. 'bi-journal-check'
  group: string; // nav group label
  roles?: readonly UserRole[]; // undefined = any authenticated user
  fields: FieldConfig[];
  listColumns: string[];
  filters?: FilterConfig[];
  /** Per-column value pickers rendered in the table header (client-side). */
  columnFilters?: readonly ColumnFilterConfig[];
  /** Columns rendered as an editable per-row dropdown that saves on pick. */
  rowEditors?: readonly RowEditorConfig[];
  readOnly?: boolean; // no create/edit/delete (audit logs)
  /** Roles for which this resource is view-only (create/edit/delete hidden). */
  readOnlyRoles?: readonly UserRole[];
  patchActions?: PatchAction[];
  hideInNav?: boolean;
  /** Optional per-role list scoping (e.g. managers see only their properties). */
  roleScope?: RoleScope;
  /**
   * Client-side scoping for a PROPERTY_MANAGER (these aren't scoped by the
   * backend). Other roles are unaffected.
   *   'property'         — rows whose propertyId is one the manager manages
   *                        (e.g. reservations, maintenance issues, preventive).
   *   'reservation'      — rows whose reservationId belongs to a reservation at
   *                        one of the manager's properties (e.g. reviews).
   *   'reservationGuest' — guest rows for guests who have a reservation at one
   *                        of the manager's properties.
   */
  managerScope?: 'property' | 'reservation' | 'reservationGuest';
}
