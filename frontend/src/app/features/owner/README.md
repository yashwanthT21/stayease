# `owner` — Owner portal (role shell)

**Not a backend module.** This is the frontend-only shell that ties the owner's
screens together. The individual owner *pages* live in the **domain** modules
(`property`, `booking`, `finance`, `stay`) — this folder only holds the glue.

## Files
- `owner-home.ts` / `.html` — the owner landing hub (KPI tiles + links to each area).
- `owner-nav.ts` — owner sidebar metadata (consumed by `layout/shell`).
- `owner.routes.ts` — wires `/owner/*` routes to the pages in the domain modules.

## Where the owner pages actually live
- Listings / Listing Manager / Calendar → `features/property/`
- Booking Summary → `features/booking/`
- Payout Statement → `features/finance/`
- Review Analytics → `features/stay/`

The shared owner data facade `owner-data.service` lives in `core/services/`
(used by these pages and by the property-manager calendar).
