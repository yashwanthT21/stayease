# `finance` — Statements & Payouts

**Backend counterpart:** `backend/stayease-backend/src/main/java/com/stayease/finance`.
**Nav group:** Finance  •  **Owner (fill in):** ________

Owner earnings: monthly statements and the payouts made against them.

## Layout
One folder per screen:

```
finance/
├── payout-statement/   payout-statement.ts · .html
└── statement-builder/  statement-builder.ts · .html
```

## Bespoke screens
- `payout-statement/` — **owner**: monthly statements and payout history for the
  signed-in owner, plus the **approve / reject** decision on an issued statement.
- `statement-builder/` — **finance**: generates a statement by deriving the
  amounts from real booking, checkout and maintenance records rather than having
  them keyed in. The owner is picked from a list, not typed as a user id.

## The approval gate
A payout can only be created against a statement the owner has **approved**
(`APPROVED`). Finance issues it, the owner approves or rejects with a reason,
Finance is notified of a rejection and corrects + re-issues. Enforced in the
backend service (`OwnerPayoutServiceImpl`), not just hidden in this UI, so the
money can't move on figures the owner disputes.

`APPROVED` / `REJECTED` are therefore absent from Finance's own status dropdown —
see `STATEMENT_STATUS_OPTIONS` in `core/models/enums.ts`.

## Registry-driven screens (`core/registry.ts`, group **Finance**)
- `owner-statements` → `/api/owner-statements` (ADMIN, FINANCE) — routed to
  `StatementBuilderComponent`.
- `owner-payouts` → `/api/owner-payouts` (ADMIN, FINANCE) — generic CRUD engine.
