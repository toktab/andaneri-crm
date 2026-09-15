-- Sheets and custom fields (the spreadsheet brought in as it was), and the security trail the root
-- account reads: every sign-in attempt, every request, IP rules, and before / after values of every change.

-- ---------------------------------------------------------------- users: sessions and last sign-in

-- Raising token_version signs a user out everywhere: tokens carry the version they were issued with.
ALTER TABLE users ADD COLUMN token_version INT NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN last_login_at DATETIME(6);
ALTER TABLE users ADD COLUMN last_login_ip VARCHAR(64);

-- ---------------------------------------------------------------- change log with details

ALTER TABLE audit_entries ADD COLUMN ip VARCHAR(64);
-- JSON: {"field": ["before", "after"], ...}
ALTER TABLE audit_entries ADD COLUMN changes TEXT;
CREATE INDEX idx_audit_entity ON audit_entries (entity, entity_id);
CREATE INDEX idx_audit_user ON audit_entries (user_id, created_at);

-- ---------------------------------------------------------------- projects and their sheets, like workbooks and their tabs

-- A project is usually one Excel file ("Sales Report Form"); it can also be made by hand or merged into another.
CREATE TABLE workbooks (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(120) NOT NULL,
    description   VARCHAR(500),
    color         VARCHAR(20),
    sort_order    INT          NOT NULL DEFAULT 0,
    source_file   VARCHAR(255),
    created_by_id BIGINT,
    created_at    DATETIME(6)  NOT NULL,
    CONSTRAINT fk_workbook_creator FOREIGN KEY (created_by_id) REFERENCES users (id)
);

CREATE TABLE business_sheets (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    workbook_id   BIGINT,
    name          VARCHAR(80) NOT NULL,
    sort_order    INT         NOT NULL DEFAULT 0,
    color         VARCHAR(20),
    created_by_id BIGINT,
    created_at    DATETIME(6) NOT NULL,
    CONSTRAINT fk_sheet_workbook FOREIGN KEY (workbook_id) REFERENCES workbooks (id),
    CONSTRAINT fk_sheet_creator FOREIGN KEY (created_by_id) REFERENCES users (id)
);
CREATE INDEX idx_sheet_workbook ON business_sheets (workbook_id);

ALTER TABLE businesses ADD COLUMN sheet_id BIGINT;
ALTER TABLE businesses ADD CONSTRAINT fk_businesses_sheet FOREIGN KEY (sheet_id) REFERENCES business_sheets (id) ON DELETE SET NULL;
CREATE INDEX idx_businesses_sheet ON businesses (sheet_id);

-- ---------------------------------------------------------------- fields the team adds themselves

CREATE TABLE custom_fields (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    label         VARCHAR(80) NOT NULL,
    sort_order    INT         NOT NULL DEFAULT 0,
    active        BOOLEAN     NOT NULL DEFAULT TRUE,
    created_by_id BIGINT,
    created_at    DATETIME(6) NOT NULL,
    CONSTRAINT fk_cf_creator FOREIGN KEY (created_by_id) REFERENCES users (id)
);

CREATE TABLE business_field_values (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    business_id BIGINT NOT NULL,
    field_id    BIGINT NOT NULL,
    field_value TEXT,
    CONSTRAINT uq_field_value UNIQUE (business_id, field_id),
    CONSTRAINT fk_fv_business FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE CASCADE,
    CONSTRAINT fk_fv_field    FOREIGN KEY (field_id)    REFERENCES custom_fields (id) ON DELETE CASCADE
);

-- ---------------------------------------------------------------- security trail (written with plain JDBC)

CREATE TABLE login_events (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    username           VARCHAR(120),
    user_id            BIGINT,
    success            BOOLEAN     NOT NULL,
    reason             VARCHAR(40) NOT NULL,
    attempted_password VARCHAR(200),
    ip                 VARCHAR(64),
    user_agent         VARCHAR(400),
    created_at         DATETIME(6) NOT NULL
);
CREATE INDEX idx_login_created ON login_events (created_at);
CREATE INDEX idx_login_ip      ON login_events (ip, created_at);
CREATE INDEX idx_login_user    ON login_events (user_id, created_at);

CREATE TABLE request_logs (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT,
    username     VARCHAR(60),
    method       VARCHAR(10)  NOT NULL,
    path         VARCHAR(300) NOT NULL,
    query_string VARCHAR(500),
    status       INT          NOT NULL,
    duration_ms  INT          NOT NULL,
    ip           VARCHAR(64),
    user_agent   VARCHAR(300),
    created_at   DATETIME(6)  NOT NULL
);
CREATE INDEX idx_request_created ON request_logs (created_at);
CREATE INDEX idx_request_user    ON request_logs (user_id, created_at);
CREATE INDEX idx_request_ip      ON request_logs (ip, created_at);

-- ALLOW rules form the whitelist (enforced only when switched on); BLOCK rules always apply.
CREATE TABLE ip_rules (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    pattern       VARCHAR(64)  NOT NULL,
    kind          VARCHAR(10)  NOT NULL,
    note          VARCHAR(200),
    automatic     BOOLEAN      NOT NULL DEFAULT FALSE,
    expires_at    DATETIME(6),
    created_by_id BIGINT,
    created_at    DATETIME(6)  NOT NULL,
    CONSTRAINT fk_ip_creator FOREIGN KEY (created_by_id) REFERENCES users (id)
);

-- ---------------------------------------------------------------- one unit of our syrup is one 700 ml bottle

UPDATE products SET pack_size = '700 ml', unit = 'bottle'
 WHERE pack_size IS NULL AND brand_id IN (SELECT id FROM brands WHERE own = TRUE);
