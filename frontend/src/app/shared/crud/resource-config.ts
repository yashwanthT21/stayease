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

/** An extra PATCH transition offered per row (notifications read/dismiss). */
export interface PatchAction {
  label: string;
  icon: string;
  /** Appended to `${apiBase}/${id}` — e.g. '/read'. */
  suffix: string;
  /** Only show the button when this predicate holds for the row. */
  showWhen?: (row: Record<string, unknown>) => boolean;
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
  readOnly?: boolean; // no create/edit/delete (audit logs)
  patchActions?: PatchAction[];
  hideInNav?: boolean;
  /** Optional per-role list scoping (e.g. managers see only their properties). */
  roleScope?: RoleScope;
}
