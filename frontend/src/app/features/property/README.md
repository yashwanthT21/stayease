# `property` — Properties & Availability

**Backend counterpart:** `backend/property-service/src/main/java/com/stayease/property`
(entities `Property`, `AvailabilityCalendar`, `PricingRule`).
**Nav group:** Property  •  **Owner (fill in):** ________

Everything about listings and their nightly availability/pricing.

## Bespoke screens in this folder
- `browse-properties.ts` / `.html` — **guest**: browse listings and start a booking.
- `property-listing.ts` / `.html` — **owner**: preview how each listing appears to guests.
- `property-manager.ts` / `.html` — **owner**: add / edit / publish / unpublish properties.
- `availability-calendar.ts` / `.html` — **owner & guest**: month-grid availability and
  nightly pricing. Shared component — the owner reaches it at `/owner/calendar`, and it is
  also embedded inside `browse-properties`.

## Registry-driven screens (`core/registry.ts`, group **Property**)
- `properties` → `/api/properties`.
- `availability` → `/api/availability` (property-manager sidebar; routed to
  `AvailabilityCalendarComponent`).
