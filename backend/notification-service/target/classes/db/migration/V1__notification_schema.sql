-- =====================================================================
-- notification-service :: V1 schema
-- Owns ONLY the notifications table, in its own database.
--
-- Note: there is NO foreign key to a users table. The user lives in the IAM
-- service's database; across a service boundary we keep user_id as a plain
-- soft reference. Enforcing referential integrity across services is the
-- caller's / an event's responsibility, not a cross-database FK.
-- =====================================================================

CREATE TABLE notifications (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    user_id      BIGINT       NOT NULL,
    message      VARCHAR(500) NOT NULL,
    category     VARCHAR(20)  NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'UNREAD',
    created_date DATETIME     NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_notification_user (user_id),
    INDEX idx_notification_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
