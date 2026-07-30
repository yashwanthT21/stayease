# `booking` — Guests & Reservations

**Backend counterpart:** `backend/stayease-backend/src/main/java/com/stayease/booking`
(entities `GuestProfile`, `Reservation`).
**Nav group:** Booking  •  **Owner (fill in):** ________

Guest profiles and the reservation lifecycle.

## Bespoke screens in this folder
- `my-reservations.ts` / `.html` — **guest**: track own reservations and leave reviews.
- `guest-profile.ts` / `.html` — **guest**: view / edit own guest profile.
- `booking.service.ts` — the guest-side booking + review API calls (shared by
  `my-reservations` and `property/browse-properties`).
- `booking-summary.ts` / `.html` — **owner**: reservations, revenue and upcoming
  stays across all of the owner's properties.

## Registry-driven screens (`core/registry.ts`, group **Booking**)
- `guests` → `/api/guests`.
- `reservations` → `/api/reservations` (with per-row Approve / Reject actions).
