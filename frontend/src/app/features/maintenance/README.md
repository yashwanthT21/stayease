# `maintenance` — Issues & Preventive Tasks

**Backend counterpart:** `backend/stayease-backend/src/main/java/com/stayease/maintenance`.
**Nav group:** Maintenance

Property upkeep: reactive maintenance issues and scheduled preventive tasks.
File names mirror the backend entities (`MaintenanceIssue`, `PreventiveMaintenance`).

## Bespoke screens in this folder

Each screen is a self-contained CRUD component (list + filters + search + a
create/edit modal + delete), the same shape as the housekeeping / stay screens:

- `maintenance-issue.ts` / `.html` — `/api/maintenance-issues`
  (backend `MaintenanceIssue`). Logs repairs; tracks priority, reporter,
  resolution date and the amount spent (which feeds the owner statement).
- `preventive-maintenance.ts` / `.html` — `/api/preventive-maintenance`
  (backend `PreventiveMaintenance`). Recurring upkeep tasks with a schedule.

A PROPERTY_MANAGER only sees rows for the properties assigned to them; the
property is chosen from a dropdown (never a raw id).

## Registry metadata

- `maintenance-issue.resource.ts` → `MAINTENANCE_ISSUE_RESOURCE`
- `preventive-maintenance.resource.ts` → `PREVENTIVE_MAINTENANCE_RESOURCE`

These `ResourceConfig`s are composed into `core/registry.ts` so the screens get
a nav entry and a guarded route; the route renders the bespoke components above.
