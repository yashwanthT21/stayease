# `property` — Properties & Availability

**Backend counterpart:** `backend/property-service/src/main/java/com/stayease/property`
(entities `Property`, `AvailabilityCalendar`; `PricingRule` has no API — see below).
**Nav group:** Property  •  **Owner (fill in):** ________

Everything about listings and their nightly availability and price.

Days before today are read-only across every role: the calendar disables past
cells, and property-service rejects a past `calendarDate` with a 400. Guests
therefore can't book a night that has already gone.

## Bespoke screens in this folder
- `browse-properties.ts` / `.html` — **guest**: browse listings and start a booking.
- `property-listing.ts` / `.html` — **owner**: preview how each listing appears to guests.
- `property-manager.ts` / `.html` — **owner**: add / edit / publish / unpublish properties.
  Check-in/check-out times default to the standard Indian hotel timings (2:00 PM →
  11:00 AM) via a one-click button, or can be set by hand.
- `availability-calendar.ts` / `.html` — **owner & guest**: month-grid availability and
  nightly price. Shared component — the owner reaches it at `/owner/calendar`, and it is
  also embedded inside `browse-properties`.

## Registry-driven screens (`core/registry.ts`, group **Property**)
- `properties` → `/api/properties`.
- `availability` → `/api/availability` (property-manager sidebar; routed to
  `AvailabilityCalendarComponent`).
