# `housekeeping` — Turnovers & Checklists

**Backend counterpart:** `backend/stayease-backend/src/main/java/com/stayease/housekeeping`.
**Nav group:** Housekeeping  •  **Owner (fill in):** ________

Turnover jobs between stays and their cleaning checklists.

## Layout
One folder per screen:

```
housekeeping/
├── turnover-assignment/          turnover-assignment.ts · .html
├── turnover-checklist/           turnover-checklist.ts · .html
└── turnover-checklist-manager/   turnover-checklist-manager.ts · .html
```

## Bespoke screens
- `turnover-assignment/` — **property manager**: the housekeeping board for
  checked-out stays (assign a housekeeper, schedule, track housekeeper/manager
  status). This is the screen the `turnovers` route renders. Assigning a turnover
  notifies the housekeeper; the housekeeper marking their work Completed notifies
  the manager.
- `turnover-checklist/` — the read-only checklist viewer, a **child component**
  rendered as a modal by `turnover-assignment` when you click "View checklist" on
  a row. Loads `/api/checklists?turnoverId=…`. It gets its own folder rather than
  sitting inside `turnover-assignment/` because it is also a screen in its own
  right, imported by name from a sibling.
- `turnover-checklist-manager/` — the housekeeper's own checklist screen, which
  the `checklists` route renders.

> The first two were split out of a single former `turnovers.ts` screen —
> assignment vs. checklist — with no change to behaviour.

## Registry-driven screens (`core/registry.ts`, group **Housekeeping**)
Both are special-cased in `app.routes.ts` to a bespoke component rather than the
generic engine:
- `turnovers` → `/api/turnovers` — nav entry; its route renders
  `TurnoverAssignmentComponent`.
- `checklists` → `/api/checklists` — nav entry; its route renders
  `TurnoverChecklistManagerComponent`.
