# `guest` — Guest navigation (role shell)

**Not a backend module.** Frontend-only shell for the customer (guest)
experience — just the sidebar metadata. The guest *screens* live in the
domain modules.

## Files
- `guest-nav.ts` — guest sidebar metadata (consumed by `layout/shell`).

## Where the guest screens actually live
- Browse properties + availability calendar → `features/property/`
- My reservations + my profile → `features/booking/`
- Notifications → registry-driven (`core/registry.ts`, group Notifications)
