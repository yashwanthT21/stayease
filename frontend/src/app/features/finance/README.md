# `finance` — Statements & Payouts

**Backend counterpart:** `backend/stayease-backend/src/main/java/com/stayease/finance`.
**Nav group:** Finance  •  **Owner (fill in):** ________

Owner earnings: monthly statements and the payouts made against them.

## Bespoke screens in this folder
- `payout-statement.ts` / `.html` — **owner**: monthly statements and payout
  history for the signed-in owner.

## Registry-driven screens (`core/registry.ts`, group **Finance**)
- `owner-statements` → `/api/owner-statements` (ADMIN, FINANCE).
- `owner-payouts` → `/api/owner-payouts` (ADMIN, FINANCE).
