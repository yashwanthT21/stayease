# `housekeeping` — Turnovers & Checklists

**Backend counterpart:** `backend/stayease-backend/src/main/java/com/stayease/housekeeping`.
**Nav group:** Housekeeping  •  **Owner (fill in):** ________

Turnover jobs between stays and their cleaning checklists.

## Bespoke screens in this folder
- `turnover-assignment.ts` / `.html` — **property manager**: the housekeeping
  board for checked-out stays (assign a housekeeper, schedule, track
  housekeeper/manager status). This is the screen the `turnovers` route renders.
- `turnover-checklist.ts` / `.html` — the read-only checklist viewer, a **child
  component** rendered as a modal by `turnover-assignment` when you click
  "View checklist" on a row. Loads `/api/checklists?turnoverId=…`.

> These two were split out of a single former `turnovers.ts` screen — assignment
> vs. checklist — with no change to behaviour.

## Registry-driven screens (`core/registry.ts`, group **Housekeeping**)
- `turnovers` → `/api/turnovers` — nav entry; its route renders
  `TurnoverAssignmentComponent` (special-cased in `app.routes.ts`).
- `checklists` → `/api/checklists` — generic CRUD list via the shared engine.
