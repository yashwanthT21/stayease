-- =====================================================================
-- StayEase :: V6 — record the OWNER's decision on a statement
--
-- An owner now approves or rejects an issued statement, and a payout may only be
-- created once they have approved it. The decision itself rides on the existing
-- status column (new APPROVED / REJECTED values — it is a VARCHAR(20) with no
-- CHECK constraint, so no change is needed there).
--
-- What is missing is the WHY. A rejection is only useful to Finance if it says
-- what to correct before re-issuing, so the owner's note is stored alongside the
-- statement rather than only being delivered as a notification that can be
-- dismissed and lost.
--
-- Nullable with no default: most statements never get a note, and an empty string
-- would be indistinguishable from "the owner left it blank".
-- =====================================================================

ALTER TABLE owner_statements
    ADD COLUMN owner_note VARCHAR(500) NULL,
    ADD COLUMN decided_date DATETIME NULL;
