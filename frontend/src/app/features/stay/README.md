# `stay` — Check-in / Check-out / Reviews

**Backend counterpart:** `backend/stayease-backend/src/main/java/com/stayease/stay`
(entities `CheckInRecord`, `CheckOutRecord`, `GuestReview`).
**Nav group:** Stay  •  **Owner (fill in):** ________

The in-stay lifecycle: arrival, departure and guest reviews.

## Bespoke screens in this folder
- `check-in.ts` / `check-in.html` — full CRUD screen for `/api/check-ins`
  (mirrors the backend `stay` module's `CheckInRecord`): list, search, and a
  create/edit/delete modal, with reservation & guest chosen from dropdowns.
- `check-out.ts` / `check-out.html` — full CRUD screen for `/api/check-outs`
  (mirrors `CheckOutRecord`): departures, damage notes and deposit release.
- `review-analytics.ts` / `.html` — **owner**: guest-satisfaction analytics
  (scores, trends) across the owner's whole portfolio.

## Nav / route config in this folder (`*.resource.ts`)
Each Stay screen keeps a small `ResourceConfig` that declares its title, icon,
nav group and role gating. These are composed into `core/registry.ts` (group
**Stay**) so the screens appear in the sidebar:
- `check-in.resource.ts` → `CHECK_IN_RESOURCE` — nav entry; its route renders
  `CheckInComponent`.
- `check-out.resource.ts` → `CHECK_OUT_RESOURCE` — nav entry; its route renders
  `CheckOutComponent`.
- `review.resource.ts` → `REVIEW_RESOURCE` — the `/reviews` list, still rendered
  by the shared generic engine (`shared/components/resource-page.ts`).

Routing lives in `app.routes.ts`, which special-cases `check-ins`/`check-outs`
to the bespoke components — exactly how `availability` and `turnovers` are
already wired. The shared response types (`CheckInRecordResponse`,
`CheckOutRecordResponse`, `GuestReviewResponse`) and their enums stay in
`core/models`, shared by every domain.
