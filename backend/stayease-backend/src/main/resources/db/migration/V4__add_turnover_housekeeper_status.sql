-- =====================================================================
-- StayEase :: V4 — split turnover status into manager + housekeeper views
--
-- The existing turnover_assignments.status stays the MANAGER status (set by the
-- manager after verifying the work). This adds a separate housekeeper_status the
-- assigned housekeeper controls (PENDING until they finish, then COMPLETED).
-- Existing rows default to PENDING.
-- =====================================================================

ALTER TABLE turnover_assignments
    ADD COLUMN housekeeper_status VARCHAR(20) NOT NULL DEFAULT 'PENDING';
