-- =====================================================================
-- StayEase :: V1 initial schema
-- Vacation Rental & Short-Stay Property Management Platform
-- Engine: InnoDB | Charset: utf8mb4 | DB: MySQL 8+
--
-- Enum-style columns are stored as VARCHAR and mapped in Java via
-- @Enumerated(EnumType.STRING). Foreign keys are enforced here at the DB
-- level even though entities reference them as plain Long id fields.
-- =====================================================================

-- ---------- 4.1 Identity & Access Management ------------------------

CREATE TABLE users (
    id      BIGINT       NOT NULL AUTO_INCREMENT,
    name    VARCHAR(150) NOT NULL,
    role    VARCHAR(30)  NOT NULL,
    email   VARCHAR(180) NOT NULL,
    phone   VARCHAR(20),
    status  VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE audit_logs (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    action      VARCHAR(150) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    logged_at   DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_audit_user FOREIGN KEY (user_id) REFERENCES users (id),
    INDEX idx_audit_user (user_id),
    INDEX idx_audit_entity (entity_type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ---------- 4.2 Property Listing & Availability ---------------------

CREATE TABLE properties (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    owner_id       BIGINT       NOT NULL,
    manager_id     BIGINT,
    title          VARCHAR(200) NOT NULL,
    type           VARCHAR(30)  NOT NULL,
    city           VARCHAR(120) NOT NULL,
    max_guests     INT          NOT NULL,
    bedrooms       INT          NOT NULL,
    bathrooms      INT          NOT NULL,
    amenities_list TEXT,
    house_rules    TEXT,
    check_in_time  TIME,
    check_out_time TIME,
    status         VARCHAR(30)  NOT NULL DEFAULT 'UNLISTED',
    PRIMARY KEY (id),
    CONSTRAINT fk_property_owner   FOREIGN KEY (owner_id)   REFERENCES users (id),
    CONSTRAINT fk_property_manager FOREIGN KEY (manager_id) REFERENCES users (id),
    INDEX idx_property_owner (owner_id),
    INDEX idx_property_city (city),
    INDEX idx_property_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE availability_calendars (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    property_id         BIGINT        NOT NULL,
    calendar_date       DATE          NOT NULL,
    availability_status VARCHAR(20)   NOT NULL DEFAULT 'AVAILABLE',
    base_price          DECIMAL(12,2) NOT NULL,
    minimum_nights      INT           NOT NULL DEFAULT 1,
    last_updated        DATETIME,
    PRIMARY KEY (id),
    CONSTRAINT fk_calendar_property FOREIGN KEY (property_id) REFERENCES properties (id),
    CONSTRAINT uq_calendar_property_date UNIQUE (property_id, calendar_date),
    INDEX idx_calendar_property (property_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE pricing_rules (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    property_id      BIGINT        NOT NULL,
    rule_type        VARCHAR(30)   NOT NULL,
    start_date       DATE,
    end_date         DATE,
    adjustment       VARCHAR(20)   NOT NULL,
    adjustment_value DECIMAL(12,2) NOT NULL,
    status           VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    PRIMARY KEY (id),
    CONSTRAINT fk_pricing_property FOREIGN KEY (property_id) REFERENCES properties (id),
    INDEX idx_pricing_property (property_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ---------- 4.3 Guest Booking & Reservation -------------------------

CREATE TABLE guest_profiles (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    user_id             BIGINT       NOT NULL,
    name                VARCHAR(150) NOT NULL,
    email               VARCHAR(180) NOT NULL,
    phone               VARCHAR(20),
    nationality         VARCHAR(80),
    verification_status VARCHAR(20)  NOT NULL DEFAULT 'UNVERIFIED',
    review_score        DECIMAL(3,2),
    booking_count       INT          NOT NULL DEFAULT 0,
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    PRIMARY KEY (id),
    CONSTRAINT fk_guest_user FOREIGN KEY (user_id) REFERENCES users (id),
    INDEX idx_guest_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE reservations (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    property_id    BIGINT        NOT NULL,
    guest_id       BIGINT        NOT NULL,
    check_in_date  DATE          NOT NULL,
    check_out_date DATE          NOT NULL,
    nights         INT           NOT NULL,
    guest_count    INT           NOT NULL,
    base_amount    DECIMAL(12,2) NOT NULL,
    cleaning_fee   DECIMAL(12,2),
    service_fee    DECIMAL(12,2),
    total_amount   DECIMAL(12,2) NOT NULL,
    booking_source VARCHAR(20)   NOT NULL DEFAULT 'DIRECT',
    status         VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    PRIMARY KEY (id),
    CONSTRAINT fk_reservation_property FOREIGN KEY (property_id) REFERENCES properties (id),
    CONSTRAINT fk_reservation_guest    FOREIGN KEY (guest_id)    REFERENCES guest_profiles (id),
    INDEX idx_reservation_property (property_id),
    INDEX idx_reservation_guest (guest_id),
    INDEX idx_reservation_status (status),
    INDEX idx_reservation_dates (check_in_date, check_out_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ---------- 4.4 Check-In, Check-Out & Guest Experience --------------

CREATE TABLE check_in_records (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    reservation_id    BIGINT      NOT NULL,
    guest_id          BIGINT      NOT NULL,
    actual_check_in   DATETIME,
    access_method     VARCHAR(20),
    welcome_pack_sent BOOLEAN     NOT NULL DEFAULT FALSE,
    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    PRIMARY KEY (id),
    CONSTRAINT fk_checkin_reservation FOREIGN KEY (reservation_id) REFERENCES reservations (id),
    CONSTRAINT fk_checkin_guest       FOREIGN KEY (guest_id)       REFERENCES guest_profiles (id),
    CONSTRAINT uq_checkin_reservation UNIQUE (reservation_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE check_out_records (
    id                 BIGINT      NOT NULL AUTO_INCREMENT,
    reservation_id     BIGINT      NOT NULL,
    actual_check_out   DATETIME,
    damage_noted       BOOLEAN     NOT NULL DEFAULT FALSE,
    damage_description TEXT,
    deposit_released   BOOLEAN     NOT NULL DEFAULT FALSE,
    status             VARCHAR(20) NOT NULL DEFAULT 'CHECKED_OUT',
    PRIMARY KEY (id),
    CONSTRAINT fk_checkout_reservation FOREIGN KEY (reservation_id) REFERENCES reservations (id),
    CONSTRAINT uq_checkout_reservation UNIQUE (reservation_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE guest_reviews (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    reservation_id    BIGINT      NOT NULL,
    guest_id          BIGINT      NOT NULL,
    cleanliness_score INT,
    accuracy_score    INT,
    location_score    INT,
    value_score       INT,
    overall_score     DECIMAL(3,2),
    comments          TEXT,
    submitted_date    DATETIME,
    status            VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED',
    PRIMARY KEY (id),
    CONSTRAINT fk_review_reservation FOREIGN KEY (reservation_id) REFERENCES reservations (id),
    CONSTRAINT fk_review_guest       FOREIGN KEY (guest_id)       REFERENCES guest_profiles (id),
    INDEX idx_review_reservation (reservation_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ---------- 4.5 Housekeeping & Turnover -----------------------------

CREATE TABLE turnover_assignments (
    id                       BIGINT      NOT NULL AUTO_INCREMENT,
    property_id              BIGINT      NOT NULL,
    check_out_reservation_id BIGINT,
    check_in_reservation_id  BIGINT,
    assigned_to_id           BIGINT,
    assigned_date            DATE,
    start_by_time            DATETIME,
    complete_by_time         DATETIME,
    status                   VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    PRIMARY KEY (id),
    CONSTRAINT fk_turnover_property      FOREIGN KEY (property_id)              REFERENCES properties (id),
    CONSTRAINT fk_turnover_checkout_res  FOREIGN KEY (check_out_reservation_id) REFERENCES reservations (id),
    CONSTRAINT fk_turnover_checkin_res   FOREIGN KEY (check_in_reservation_id)  REFERENCES reservations (id),
    CONSTRAINT fk_turnover_assignee      FOREIGN KEY (assigned_to_id)           REFERENCES users (id),
    INDEX idx_turnover_property (property_id),
    INDEX idx_turnover_assignee (assigned_to_id),
    INDEX idx_turnover_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE turnover_checklists (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    turnover_id BIGINT       NOT NULL,
    task_name   VARCHAR(150) NOT NULL,
    category    VARCHAR(20)  NOT NULL,
    completed   BOOLEAN      NOT NULL DEFAULT FALSE,
    notes       TEXT,
    status      VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    PRIMARY KEY (id),
    CONSTRAINT fk_checklist_turnover FOREIGN KEY (turnover_id) REFERENCES turnover_assignments (id),
    INDEX idx_checklist_turnover (turnover_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ---------- 4.6 Maintenance & Property Operations -------------------

CREATE TABLE maintenance_issues (
    id                     BIGINT      NOT NULL AUTO_INCREMENT,
    property_id            BIGINT      NOT NULL,
    reported_by_id         BIGINT      NOT NULL,
    reported_by_type       VARCHAR(20) NOT NULL,
    category               VARCHAR(20) NOT NULL,
    description            TEXT,
    priority               VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    assigned_contractor_id BIGINT,
    reported_date          DATETIME    NOT NULL,
    resolved_date          DATETIME,
    status                 VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    PRIMARY KEY (id),
    CONSTRAINT fk_issue_property    FOREIGN KEY (property_id)    REFERENCES properties (id),
    CONSTRAINT fk_issue_reported_by FOREIGN KEY (reported_by_id) REFERENCES users (id),
    INDEX idx_issue_property (property_id),
    INDEX idx_issue_status (status),
    INDEX idx_issue_priority (priority)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE preventive_maintenance (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    property_id         BIGINT       NOT NULL,
    task_name           VARCHAR(150) NOT NULL,
    frequency           VARCHAR(20)  NOT NULL,
    next_scheduled_date DATE,
    last_completed_date DATE,
    status              VARCHAR(20)  NOT NULL DEFAULT 'SCHEDULED',
    PRIMARY KEY (id),
    CONSTRAINT fk_preventive_property FOREIGN KEY (property_id) REFERENCES properties (id),
    INDEX idx_preventive_property (property_id),
    INDEX idx_preventive_next (next_scheduled_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ---------- 4.7 Owner Payout & Financial Management -----------------

CREATE TABLE owner_statements (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    owner_id         BIGINT        NOT NULL,
    period           VARCHAR(20)   NOT NULL,
    gross_revenue    DECIMAL(14,2),
    platform_fee     DECIMAL(12,2),
    management_fee   DECIMAL(12,2),
    cleaning_revenue DECIMAL(12,2),
    maintenance_cost DECIMAL(12,2),
    net_payout       DECIMAL(14,2),
    generated_date   DATETIME,
    status           VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    PRIMARY KEY (id),
    CONSTRAINT fk_statement_owner FOREIGN KEY (owner_id) REFERENCES users (id),
    INDEX idx_statement_owner (owner_id),
    INDEX idx_statement_period (period)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE owner_payouts (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    statement_id     BIGINT        NOT NULL,
    owner_id         BIGINT        NOT NULL,
    amount           DECIMAL(14,2) NOT NULL,
    payment_date     DATE,
    bank_account_ref VARCHAR(120),
    status           VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    PRIMARY KEY (id),
    CONSTRAINT fk_payout_statement FOREIGN KEY (statement_id) REFERENCES owner_statements (id),
    CONSTRAINT fk_payout_owner     FOREIGN KEY (owner_id)     REFERENCES users (id),
    INDEX idx_payout_statement (statement_id),
    INDEX idx_payout_owner (owner_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ---------- 4.8 Notifications & Alerts ------------------------------

CREATE TABLE notifications (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    user_id      BIGINT      NOT NULL,
    message      VARCHAR(500) NOT NULL,
    category     VARCHAR(20) NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'UNREAD',
    created_date DATETIME    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES users (id),
    INDEX idx_notification_user (user_id),
    INDEX idx_notification_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
