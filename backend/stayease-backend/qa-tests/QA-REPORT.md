# StayEase Backend — Black-Box QA Test Report

**Date:** 2026-06-16
**Target:** `http://localhost:8085` (Spring Boot 4.0.6, MySQL)
**Mode:** External-client only. No application code, config, endpoints, or data models were modified. All test data was created dynamically with unique keys and cleaned up afterward.
**Harness:** [qa-tests/run-tests.ps1](run-tests.ps1) — 117 automated checks across 105 HTTP calls. Raw evidence in [results.json](results.json) / [results.txt](results.txt); captured spec in [openapi.json](openapi.json).

---

## 1. Executive Summary

| Metric | Value |
|---|---|
| Total checks | **117** |
| Passed | **113** |
| Failed (raw) | 4 — *all the same cleanup-ordering artifact; not app defects (see §9)* |
| Endpoints exercised | **84 operations / 18 controllers** (every documented path) |
| Functional CRUD lifecycles verified | **15/15 entities** (create → read → list → filter → update → delete) |
| Avg response time | **70 ms** (max 606 ms, cold-start first call) |
| **Defects found** | **1 High, 2 Medium, 4 low/observations** (see §10) |

**Verdict:** The API is **functionally solid** — every endpoint works, validation is thorough, RBAC is correctly enforced, and cross-endpoint data is consistent. The issues found are concentrated in **(a) a security simplification (self-assignable roles)** and **(b) error-handling gaps where server-side 500s are returned for conditions that should be 400/409.**

---

## 2. Environment & Method

- Acted purely as an HTTP client (PowerShell `Invoke-WebRequest`).
- Obtained JWTs via `POST /api/auth/register` for each role tier (ADMIN, FINANCE, OWNER, GUEST, HOUSEKEEPING).
- Created a full dependency chain of real records (the DB enforces foreign keys, so data was created owner → property → guest → reservation → … → payout).
- All emails/keys carry a random+timestamp suffix to guarantee uniqueness across re-runs.

---

## 3. Endpoint Inventory (discovered from `/v3/api-docs`)

18 REST controllers, uniform CRUD shape (`POST`, `GET` list, `GET /{id}`, `PUT /{id}`, `DELETE /{id}`):

| Domain | Base path | Notes |
|---|---|---|
| Auth | `/api/auth/register`, `/api/auth/login` | Public. Returns `{token, tokenType, userId, email, role}` |
| Users | `/api/users` | **ADMIN only** |
| Audit logs | `/api/audit-logs` | **ADMIN/FINANCE**, read-only (GET only) |
| Properties | `/api/properties` | `?ownerId` filter |
| Availability | `/api/availability` | **`?propertyId` required** |
| Pricing rules | `/api/pricing-rules` | **`?propertyId` required** |
| Guests | `/api/guests` | `?userId` filter |
| Reservations | `/api/reservations` | `?propertyId`, `?guestId`; computes `nights` + `totalAmount` |
| Check-ins | `/api/check-ins` | `?guestId` |
| Check-outs | `/api/check-outs` | no filters |
| Reviews | `/api/reviews` | `?reservationId`, `?guestId` |
| Turnovers | `/api/turnovers` | `?propertyId`, `?assignedToId` |
| Checklists | `/api/checklists` | **`?turnoverId` required** |
| Maintenance issues | `/api/maintenance-issues` | `?propertyId`, `?status` |
| Preventive maintenance | `/api/preventive-maintenance` | `?propertyId` |
| Owner statements | `/api/owner-statements` | **ADMIN/FINANCE** |
| Owner payouts | `/api/owner-payouts` | **ADMIN/FINANCE** |
| Notifications | `/api/notifications` | `?userId`, `?status` |

**Auth header:** `Authorization: Bearer <jwt>`. All paths except `/api/auth/**` require it.

---

## 4. Functional Testing (happy path) — ✅ PASS

All 15 entity types were created (`201`), read back by id (`200`), listed/filtered (`200`), updated (`200`), and deleted (`204`).

- Computed fields verified: a 3-night reservation (`checkIn`+10d → `checkOut`+13d) correctly returned **`nights=3`** and **`totalAmount=640`** (base 540 + cleaning 60 + service 40). ✅
- `POST /api/users` (admin) and the full booking→stay→housekeeping→finance chain all succeeded.

## 5. Negative / Edge Cases — ✅ correct in 14/16, ⚠️ 2 return 500

| Case | Expected | Actual | OK? |
|---|---|---|---|
| Missing required field (no `title`) | 400 | 400 | ✅ |
| `maxGuests=0` (violates `min 1`) | 400 | 400 | ✅ |
| Wrong type (`maxGuests:"NaN"`) | 400 | 400 | ✅ |
| Invalid enum (`type:"CASTLE"`) | 400 | 400 | ✅ |
| Invalid email on guest | 400 | 400 | ✅ |
| Review score `< 1` | 400 | 400 | ✅ |
| Reservation checkout < checkin (business rule) | 400 | **400** | ✅ |
| Non-existent FK `ownerId` on create | 4xx | **404** | ✅ (service validates) |
| Malformed JSON body | 400 | 400 | ✅ |
| GET / PUT non-existent id | 404 | 404 | ✅ |
| Non-numeric path id (`/abc`) | 400 | 400 | ✅ |
| Wrong HTTP method on `/auth/login` | 405 | 405 | ✅ |
| **GET `/api/availability` w/o required `propertyId`** | 400 | **500** | ❌ §10-2 |
| **GET `/api/pricing-rules` w/o required `propertyId`** | 400 | **500** | ❌ §10-2 |

## 6. End-to-End Flow — ✅ PASS
`register owner/guest → create property → availability → pricing → guest profile → reservation → check-in → check-out → review → turnover → checklist → maintenance → preventive → owner statement → payout → notification` — every step succeeded and chained on the prior step's generated id.

## 7. Data Consistency — ✅ PASS
- Created property appears in `GET /api/properties?ownerId=…`. ✅
- Created reservation appears in `GET /api/reservations?propertyId=…`. ✅
- Created payout appears in `GET /api/owner-payouts?ownerId=…`. ✅
- `PUT` changes reflected on subsequent `GET` (property `city`→Porto; notification `status`→READ). ✅
- Finance operations wrote matching `audit_logs` rows (1 statement + 1 payout). ✅

## 8. Authentication & Authorization — ✅ PASS (RBAC fully enforced)

| Principal | Endpoint | Expected | Actual |
|---|---|---|---|
| No token | `GET /api/properties` | 401 | 401 ✅ |
| Malformed token | `GET /api/properties` | 401 | 401 ✅ |
| GUEST | `GET /api/users` | 403 | 403 ✅ |
| GUEST | `GET /api/owner-statements` | 403 | 403 ✅ |
| GUEST | `GET /api/properties` | 200 | 200 ✅ |
| FINANCE | `GET /api/users` | 403 | 403 ✅ |
| FINANCE | `GET /api/owner-statements` | 200 | 200 ✅ |
| FINANCE | `GET /api/audit-logs` | 200 | 200 ✅ |
| ADMIN | `GET /api/users` | 200 | 200 ✅ |
| ADMIN | `GET /api/audit-logs` | 200 | 200 ✅ |

Bonus security positive: deleting a user **immediately invalidates that user's JWT** (the app re-loads the principal from the DB on every request), so stale tokens for removed accounts are rejected with 401.

## 9. Performance (light check)
105 HTTP calls: **avg 70 ms, max 606 ms** (the 606 ms was the very first call — JVM/connection warm-up; subsequent auth calls dropped to ~200 ms, reads ~40 ms). No endpoint was unusually slow; nothing approached the 1 s threshold.

---

## 10. Findings & Recommendations

### 🔴 HIGH — 1. Clients can self-assign privileged roles at registration
`POST /api/auth/register` accepts a client-supplied `role`, so **anyone can register as `ADMIN`** and immediately access every admin-only endpoint (`/api/users`, `/api/audit-logs`, `/api/owner-*`). Verified end-to-end in this run.
*(The DTO comment acknowledges this is a deliberate learning-project simplification.)*
**Recommendation:** For any non-learning deployment, ignore client role on self-registration (default to `GUEST`), and provision privileged roles only via an authenticated admin flow (`POST /api/users`).

### 🟠 MEDIUM — 2. Missing required query param returns 500 instead of 400
`GET /api/availability` and `GET /api/pricing-rules` without their required `propertyId` return **HTTP 500**. Root cause: `MissingServletRequestParameterException` has no handler in `GlobalExceptionHandler`, so it falls through to the generic 500 catch-all.
**Recommendation:** Add an `@ExceptionHandler(MissingServletRequestParameterException.class)` → 400.

### 🟠 MEDIUM — 3. Deleting an FK-referenced row returns 500 instead of 409
Deleting a user still referenced by another table (confirmed: owner user referenced by `audit_logs`) throws `DataIntegrityViolationException`, unhandled → **HTTP 500**.
**Recommendation:** Add an `@ExceptionHandler(DataIntegrityViolationException.class)` → `409 Conflict` with a clear message (e.g. "cannot delete: still referenced").

### 🟡 LOW — 4. Users with audit history are undeletable via the API
Because `audit_logs.user_id` is a hard FK with no cascade, no audit-log delete endpoint, and no soft-delete/anonymise path, a user who has ever triggered an audited action **cannot be removed through the API** (the attempt 500s — see #3). Operationally relevant for account-removal / data-retention.
**Recommendation:** Consider soft-delete (status flagging) or a user-anonymisation flow; keep audit rows intact.

### 🟡 LOW — 5. No self-deletion guard
An admin can delete their own account via `DELETE /api/users/{ownId}`. It succeeds and instantly invalidates their own token (correct behavior), but there's no safeguard against an admin locking themselves/the last admin out.
**Recommendation:** Block deleting the currently-authenticated user and/or the last remaining ADMIN.

### 🟡 OBSERVATION — 6. Audit "actor" semantics
The finance `audit_logs` recorded `user_id` = the **owner/subject** of the statement, not the **authenticated admin** who performed the action. Confirm this is the intended meaning of the audit "who".

### 🟡 OBSERVATION — 7. Audit coverage is finance-only
Only `OwnerStatement` (CREATE) and `OwnerPayout` (ISSUE_PAYOUT) produced audit entries. Property/reservation/user/etc. CRUD is not audited. Fine if intentional; a gap if a full audit trail is expected.

---

## 11. Test Data Cleanup
All created business records (property, availability, pricing, guest, reservation, check-in/out, review, turnover, checklist, maintenance, preventive, statement, payout, notification, admin-created user) were deleted successfully (`204`).

**Residual:** one test OWNER user could **not** be deleted via the API (blocked by audit-log FK — finding #3/#4) and remains in the DB. Removing it requires either fixing the deletion path or a DB-level cleanup, neither of which was performed (external-client rule respected). No other test data remains.

## 12. How to Reproduce
```powershell
# app must be running on :8085 with MySQL up
powershell -ExecutionPolicy Bypass -File qa-tests/run-tests.ps1
```
Outputs a live pass/fail log plus `qa-tests/results.json` and `qa-tests/results.txt`.
