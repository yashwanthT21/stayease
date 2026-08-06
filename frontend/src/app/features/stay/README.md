# `stay` — Check-in / Check-out / Reviews

**Backend counterpart:** `backend/stayease-backend/src/main/java/com/stayease/stay`
(entities `CheckInRecord`, `CheckOutRecord`, `GuestReview`).
**Nav group:** Stay  •  **Owner (fill in):** ________

The in-stay lifecycle: arrival, departure and guest reviews.

## Layout
One folder per screen — a screen's component, template and nav config all live
together:

```
stay/
├── check-in/          check-in.ts · .html · .resource.ts
├── check-out/         check-out.ts · .html · .resource.ts
├── review-analytics/  review-analytics.ts · .html
└── reviews/           review.resource.ts
```

## Bespoke screens
- `check-in/` — full CRUD screen for `/api/check-ins` (mirrors the backend `stay`
  module's `CheckInRecord`): list, search, and a create/edit/delete modal, with
  reservation & guest chosen from dropdowns.
- `check-out/` — full CRUD screen for `/api/check-outs` (mirrors
  `CheckOutRecord`): departures and damage notes. Status offers only
  `CHECKED_OUT`; damage is recorded by the `damageNoted` flag.
- `review-analytics/` — **owner**: guest-satisfaction analytics (scores, trends)
  across the owner's whole portfolio.

## Nav / route config (`*.resource.ts`)
Each Stay screen keeps a small `ResourceConfig` that declares its title, icon,
nav group and role gating, alongside the component it configures. These are
composed into `core/registry.ts` (group **Stay**) so the screens appear in the
sidebar:
- `check-in/check-in.resource.ts` → `CHECK_IN_RESOURCE` — nav entry; its route
  renders `CheckInComponent`.
- `check-out/check-out.resource.ts` → `CHECK_OUT_RESOURCE` — nav entry; its route
  renders `CheckOutComponent`.
- `reviews/review.resource.ts` → `REVIEW_RESOURCE` — the `/reviews` list. It has
  its own folder because it *is* the whole screen: there's no bespoke component,
  it's rendered by the shared generic engine
  (`shared/components/resource-page.ts`). Distinct from `review-analytics/`,
  which is the owner's separate analytics view.

Routing lives in `app.routes.ts`, which special-cases `check-ins`/`check-outs`
to the bespoke components — exactly how `availability` and `turnovers` are
already wired. The shared response types (`CheckInRecordResponse`,
`CheckOutRecordResponse`, `GuestReviewResponse`) and their enums stay in
`core/models`, shared by every domain.
