# `maintenance` — Issues & Preventive Tasks

**Backend counterpart:** `backend/stayease-backend/src/main/java/com/stayease/maintenance`.
**Nav group:** Maintenance

Property upkeep: reactive maintenance issues and scheduled preventive tasks.
File names mirror the backend entities (`MaintenanceIssue`, `PreventiveMaintenance`).

## Layout
One folder per screen, each holding its component, template and registry config:

```
maintenance/
├── maintenance-issue/       maintenance-issue.ts · .html · .resource.ts
└── preventive-maintenance/  preventive-maintenance.ts · .html · .resource.ts
```

## Bespoke screens

Each screen is a self-contained CRUD component (list + filters + search + a
create/edit modal + delete), the same shape as the housekeeping / stay screens:

- `maintenance-issue/` — `/api/maintenance-issues` (backend `MaintenanceIssue`).
  Logs repairs; tracks priority, reporter, resolution date and the amount spent
  (which feeds the owner statement).
- `preventive-maintenance/` — `/api/preventive-maintenance` (backend
  `PreventiveMaintenance`). Recurring upkeep tasks with a schedule.

A PROPERTY_MANAGER only sees rows for the properties assigned to them; the
property is chosen from a dropdown (never a raw id).

## Registry metadata

- `maintenance-issue/maintenance-issue.resource.ts` → `MAINTENANCE_ISSUE_RESOURCE`
- `preventive-maintenance/preventive-maintenance.resource.ts` →
  `PREVENTIVE_MAINTENANCE_RESOURCE`

These `ResourceConfig`s are composed into `core/registry.ts` so the screens get
a nav entry and a guarded route; the route renders the bespoke components above.
