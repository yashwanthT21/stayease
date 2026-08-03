import { ResourceConfig } from '../../shared/crud/resource-config';
import { PREVENTIVE_FREQUENCIES, PREVENTIVE_STATUSES } from '../../core/models/enums';

// Reference helper: point a dropdown at another resource's list for id labels.
const propertyRef = { resourceKey: 'properties', labelFields: ['title', 'city'] };

/**
 * Preventive maintenance screen — mirrors the backend `maintenance` module's
 * PreventiveMaintenance entity. Drives the generic list/CRUD screen via the
 * shared `ResourcePageComponent`; composed into the app-wide list in
 * `core/registry.ts`.
 */
export const PREVENTIVE_MAINTENANCE_RESOURCE: ResourceConfig = {
  key: 'preventive-maintenance',
  apiBase: '/api/preventive-maintenance',
  title: 'Preventive Maintenance',
  singular: 'Preventive task',
  icon: 'bi-clipboard-check',
  group: 'Maintenance',
  managerScope: 'property',
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
};
