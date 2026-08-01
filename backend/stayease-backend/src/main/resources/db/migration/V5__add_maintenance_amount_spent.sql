-- =====================================================================
-- StayEase :: V5 — record repair cost on a maintenance issue
--
-- A maintenance issue can now carry the amount spent fixing it (logged by the
-- manager once the work is done). This feeds the owner statement's maintenance
-- cost. Nullable — existing/open issues have no cost yet (treated as 0).
-- =====================================================================

ALTER TABLE maintenance_issues
    ADD COLUMN amount_spent DECIMAL(12,2) NULL AFTER resolved_date;
