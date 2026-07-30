# `maintenance` — Maintenance & Preventive tasks

**Backend counterpart:** `backend/stayease-backend/src/main/java/com/stayease/maintenance`.
**Nav group:** Maintenance  •  **Owner (fill in):** ________

Reactive maintenance issues and scheduled preventive tasks.

## Screens
This module has **no bespoke component** — its entire UI is **registry-driven**,
rendered by the shared engine `shared/components/resource-page.ts` from config in
`core/registry.ts` (group **Maintenance**):

- `maintenance-issues` → `/api/maintenance-issues` — report/track issues
  (category, priority, contractor, status filters).
- `preventive-maintenance` → `/api/preventive-maintenance` — recurring
  scheduled tasks.

> To explain this module in an interview, show the two entries in
> `core/registry.ts` and how `ResourcePageComponent` turns that config into a
> full list + create/edit/delete screen.
