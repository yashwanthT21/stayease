-- =====================================================================
-- StayEase :: V3  — extract property + notification into microservices
--
-- property and notification now live in their own services, each with its own
-- database (stayease_property, stayease_notification). This migration removes
-- them from the monolith's schema:
--
--  1. Drop the cross-service foreign keys from the tables that reference a
--     property. Their property_id columns REMAIN as plain (soft) references —
--     validated at runtime by a REST call to property-service, not a DB FK.
--  2. Drop the tables whose data has moved out of this database.
--
-- These four FKs were created in V1, so they are guaranteed to exist here.
-- =====================================================================

ALTER TABLE reservations           DROP FOREIGN KEY fk_reservation_property;
ALTER TABLE turnover_assignments   DROP FOREIGN KEY fk_turnover_property;
ALTER TABLE maintenance_issues     DROP FOREIGN KEY fk_issue_property;
ALTER TABLE preventive_maintenance DROP FOREIGN KEY fk_preventive_property;

-- availability_calendars and pricing_rules FK-reference properties, so drop them
-- before properties. notifications is independent.
DROP TABLE IF EXISTS availability_calendars;
DROP TABLE IF EXISTS pricing_rules;
DROP TABLE IF EXISTS notifications;
DROP TABLE IF EXISTS properties;
