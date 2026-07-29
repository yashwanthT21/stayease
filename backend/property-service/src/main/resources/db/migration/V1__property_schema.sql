-- =====================================================================
-- property-service :: V1 schema
-- Owns properties, availability_calendars, pricing_rules in its own database.
--
-- FKs EXIST between these three tables (they live in the same database and form
-- one bounded context) but there is NO foreign key to a users table: owner_id /
-- manager_id are soft references to users owned by the IAM service. Crossing a
-- service boundary with a database FK is exactly what database-per-service
-- forbids.
-- =====================================================================

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
