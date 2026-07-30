# `notification` — Notifications

**Backend counterpart:** `backend/notification-service/src/main/java/com/stayease/notification`.
**Nav group:** Notifications  •  **Owner (fill in):** ________

User-facing notifications with read / dismiss transitions.

## Screens
This module has **no bespoke component** — its UI is **registry-driven**,
rendered by the shared engine `shared/components/resource-page.ts` from config in
`core/registry.ts` (group **Notifications**):

- `notifications` → `/api/notifications` — list with per-row **Mark read** and
  **Dismiss** actions; guests see only their own (auto-scoped by `userId`).

> To explain this module in an interview, show the `notifications` entry in
> `core/registry.ts` (note the `patchActions` and `roleScope`) and how
> `ResourcePageComponent` consumes them.
