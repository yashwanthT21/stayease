# `booking` — Guests & Reservations

**Backend counterpart:** `backend/stayease-backend/src/main/java/com/stayease/booking`
(entities `GuestProfile`, `Reservation`).
**Nav group:** Booking  •  **Owner (fill in):** ________

Guest profiles and the reservation lifecycle.

## Layout
One folder per screen, plus a `shared/` folder for the code more than one screen
uses:

```
booking/
├── shared/            booking.service.ts
├── booking-summary/   booking-summary.ts · .html
├── guest-profile/     guest-profile.ts · .html
└── my-reservations/   my-reservations.ts · .html
```

## Bespoke screens
- `my-reservations/` — **guest**: track own reservations and leave reviews.
- `guest-profile/` — **guest**: view / edit own guest profile. Account standing
  shows verification only — the one fact there a guest can act on.
- `booking-summary/` — **owner**: reservations, revenue and upcoming stays across
  all of the owner's properties.

## Shared
- `shared/booking.service.ts` — the guest-side booking + review API calls. It
  lives here rather than in a screen folder because it is used from two places,
  one of them in another feature: `my-reservations/` and
  `property/browse-properties/`.

## Registry-driven screens (`core/registry.ts`, group **Booking**)
- `guests` → `/api/guests`.
- `reservations` → `/api/reservations` (with per-row Approve / Reject actions).
