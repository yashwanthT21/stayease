# `features/` — module map

The frontend is split into **domain modules that mirror the backend**, so each
teammate can open the same-named folder in `backend/` and here in `frontend/`.

| Frontend folder | Backend counterpart | Bespoke screens? |
|---|---|---|
| `iam/` | `stayease-backend/.../iam` (User, AuditLog) + gateway auth | login, register |
| `property/` | `property-service/.../property` | browse, listing, manager, calendar |
| `booking/` | `stayease-backend/.../booking` (GuestProfile, Reservation) | my-reservations, guest-profile, booking-summary |
| `stay/` | `stayease-backend/.../stay` (CheckIn/Out, GuestReview) | review-analytics |
| `housekeeping/` | `stayease-backend/.../housekeeping` | turnovers |
| `maintenance/` | `stayease-backend/.../maintenance` | none (registry-driven) |
| `finance/` | `stayease-backend/.../finance` | payout-statement |
| `notification/` | `notification-service/.../notification` | none (registry-driven) |

Three folders here are **frontend-only** (no backend module) — they wire the
domain screens into per-role experiences:

- `owner/` — the owner portal shell (home hub, nav, routing).
- `guest/` — the guest navigation shell.
- `dashboard/` — the shared post-login landing page.

## Two ways a screen gets built

1. **Bespoke component** — a hand-written `.ts` + `.html` in a module folder
   (e.g. `property/property-manager`). Listed in each module's README.
2. **Registry-driven CRUD** — most operational tables (reservations, check-ins,
   maintenance, notifications, users, …) have **no component of their own**.
   One shared engine, `shared/components/resource-page.ts`, renders them from
   config declared in `core/registry.ts`. Each registry entry carries a
   `group:` that names its domain (Property, Booking, Stay, …). So a module's
   "frontend code" is often *its registry entries*, not a bespoke file.

## Shared infrastructure (parallels backend `common` / `config` / `security`)

- `core/` — auth runtime, route guards, HTTP interceptors, DTO/enum models,
  `crud.service`, `owner-data.service`, and the `registry.ts` config.
- `shared/` — the generic CRUD engine, reusable UI widgets (`shared/ui`), the
  toast container, and pipes.
- `layout/` — the authenticated app shell (sidebar + top bar).

> Reorganization note: routes, route paths, component selectors and behaviour
> are unchanged — only file locations moved. Nothing in `backend/` was touched.
