import { ResourceConfig } from '../../shared/crud/resource-config';
import { MAINTENANCE_CATEGORIES, MAINTENANCE_PRIORITIES, MAINTENANCE_STATUSES, REPORTED_BY_TYPES } from '../../core/models/enums';

// Reference helper: point a dropdown at another resource's list for id labels.
const propertyRef = { resourceKey: 'properties', labelFields: ['title', 'city'] };

/**
 * Maintenance issue screen — mirrors the backend `maintenance` module's
 * MaintenanceIssue entity. Drives the generic list/CRUD screen via the shared
 * `ResourcePageComponent`; composed into the app-wide list in `core/registry.ts`.
 */
export const MAINTENANCE_ISSUE_RESOURCE: ResourceConfig = {
  key: 'maintenance-issues',
  apiBase: '/api/maintenance-issues',
  title: 'Maintenance Issues',
  singular: 'Issue',
  icon: 'bi-tools',
  group: 'Maintenance',
  managerScope: 'property',
  listColumns: ['id', 'propertyId', 'category', 'priority', 'reportedByType', 'reportedDate', 'amountSpent', 'status'],
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
    { key: 'amountSpent', label: 'Amount spent', type: 'money', min: 0, help: 'Repair cost logged on resolution; feeds the owner statement.' },
    { key: 'status', label: 'Status', type: 'select', options: MAINTENANCE_STATUSES, help: 'Defaults to OPEN.' },
    { key: 'reportedDate', label: 'Reported', type: 'datetime', hideInForm: true },
  ],
};
