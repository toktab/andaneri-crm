-- The whole CRM schema. Written in the SQL both MySQL 8 and H2 (MySQL mode, used by the demo profile
-- and the tests) accept: no ENGINE/CHARSET clauses (InnoDB and utf8mb4 are MySQL 8's defaults), and
-- no column named after an H2 reserved word (key, value, user, day, month, year, end, row...).
--
-- One business has many contacts, activities, tasks, product usages, interests, purchases, comments
-- and status changes. Nothing that happened is ever overwritten: history is rows, not edited cells.

CREATE TABLE users (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(60)  NOT NULL,
    full_name     VARCHAR(120) NOT NULL,
    phone         VARCHAR(40),
    role          VARCHAR(20)  NOT NULL,
    password_hash VARCHAR(200) NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    DATETIME(6)  NOT NULL,
    CONSTRAINT uq_users_username UNIQUE (username)
);

-- ---------------------------------------------------------------- catalog

CREATE TABLE business_types (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    name_ka    VARCHAR(80) NOT NULL,
    name_en    VARCHAR(80) NOT NULL,
    sort_order INT         NOT NULL DEFAULT 0,
    active     BOOLEAN     NOT NULL DEFAULT TRUE
);

-- Syrup, fruit puree, coffee cream, sauce... what a business can be asked "do you use it?" about.
CREATE TABLE product_categories (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    name_ka    VARCHAR(80) NOT NULL,
    name_en    VARCHAR(80) NOT NULL,
    sort_order INT         NOT NULL DEFAULT 0,
    active     BOOLEAN     NOT NULL DEFAULT TRUE
);

-- Andaneri itself (own = TRUE) and every competitor: Monin, 1883, Black Sea...
CREATE TABLE brands (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(80) NOT NULL,
    own        BOOLEAN     NOT NULL DEFAULT FALSE,
    active     BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uq_brands_name UNIQUE (name)
);

-- One shared flavor list, so "they use Monin Vanilla" can be matched to "our Vanilla, 18 GEL".
CREATE TABLE flavors (
    id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    name_ka VARCHAR(100) NOT NULL,
    name_en VARCHAR(100) NOT NULL,
    active  BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE products (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    brand_id      BIGINT        NOT NULL,
    category_id   BIGINT        NOT NULL,
    section_ka    VARCHAR(80),
    section_en    VARCHAR(80),
    name_ka       VARCHAR(160)  NOT NULL,
    name_en       VARCHAR(160)  NOT NULL,
    pack_size     VARCHAR(40),
    unit          VARCHAR(20),
    price         DECIMAL(10,2),
    juice_percent INT,
    status        VARCHAR(20)   NOT NULL,
    description   TEXT,
    sort_order    INT           NOT NULL DEFAULT 0,
    created_at    DATETIME(6)   NOT NULL,
    CONSTRAINT fk_products_brand    FOREIGN KEY (brand_id)    REFERENCES brands (id),
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES product_categories (id)
);

CREATE TABLE product_flavors (
    product_id BIGINT NOT NULL,
    flavor_id  BIGINT NOT NULL,
    PRIMARY KEY (product_id, flavor_id),
    CONSTRAINT fk_pf_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT fk_pf_flavor  FOREIGN KEY (flavor_id)  REFERENCES flavors (id)
);

-- What a product is good for (cocktails, lemonades, iced tea...), from the 2026 trends sheet.
CREATE TABLE product_applications (
    product_id  BIGINT      NOT NULL,
    application VARCHAR(30) NOT NULL,
    PRIMARY KEY (product_id, application),
    CONSTRAINT fk_pa_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
);

-- ---------------------------------------------------------------- businesses

CREATE TABLE businesses (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    name               VARCHAR(160) NOT NULL,
    type_id            BIGINT,
    status             VARCHAR(30)  NOT NULL,
    priority           VARCHAR(10)  NOT NULL,
    address            VARCHAR(255),
    city               VARCHAR(80),
    district           VARCHAR(80),
    phone              VARCHAR(60),
    email              VARCHAR(120),
    website            VARCHAR(200),
    maps_url           VARCHAR(500),
    latitude           DECIMAL(9,6),
    longitude          DECIMAL(9,6),
    id_code            VARCHAR(40),
    legal_name         VARCHAR(200),
    branches           INT,
    visit_hours        VARCHAR(120),
    notes              TEXT,
    menu_change        VARCHAR(200),
    switch_openness    VARCHAR(20)  NOT NULL,
    price_sensitivity  VARCHAR(20)  NOT NULL,
    satisfaction       VARCHAR(20)  NOT NULL,
    competitor_notes   TEXT,
    reorder_days       INT,
    assigned_to_id     BIGINT,
    created_by_id      BIGINT       NOT NULL,
    -- Kept up to date by the services, so lists can filter and sort on them without joins.
    last_contact_at    DATETIME(6),
    last_purchase_date DATE,
    purchase_count     INT          NOT NULL DEFAULT 0,
    archived           BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at         DATETIME(6)  NOT NULL,
    updated_at         DATETIME(6)  NOT NULL,
    version            BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT fk_businesses_type     FOREIGN KEY (type_id)        REFERENCES business_types (id),
    CONSTRAINT fk_businesses_assigned FOREIGN KEY (assigned_to_id) REFERENCES users (id),
    CONSTRAINT fk_businesses_creator  FOREIGN KEY (created_by_id)  REFERENCES users (id)
);
CREATE INDEX idx_businesses_name     ON businesses (name);
CREATE INDEX idx_businesses_status   ON businesses (status);
CREATE INDEX idx_businesses_district ON businesses (district);
CREATE INDEX idx_businesses_assigned ON businesses (assigned_to_id);
CREATE INDEX idx_businesses_id_code  ON businesses (id_code);
CREATE INDEX idx_businesses_phone    ON businesses (phone);

-- What they make: cocktails, lemonades, coffee... Drives which of our products to suggest.
CREATE TABLE business_drink_types (
    business_id BIGINT      NOT NULL,
    drink_type  VARCHAR(30) NOT NULL,
    PRIMARY KEY (business_id, drink_type),
    CONSTRAINT fk_bdt_business FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE CASCADE
);

CREATE TABLE contacts (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    business_id       BIGINT       NOT NULL,
    name              VARCHAR(120) NOT NULL,
    role_title        VARCHAR(80),
    phone             VARCHAR(60),
    email             VARCHAR(120),
    preferred_channel VARCHAR(20)  NOT NULL,
    decision_maker    BOOLEAN      NOT NULL DEFAULT FALSE,
    notes             TEXT,
    created_at        DATETIME(6)  NOT NULL,
    CONSTRAINT fk_contacts_business FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE CASCADE
);
CREATE INDEX idx_contacts_business ON contacts (business_id);

-- "Do they use fruit puree?" Yes / no / sometimes / unknown, one answer per category.
CREATE TABLE category_usages (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    business_id BIGINT      NOT NULL,
    category_id BIGINT      NOT NULL,
    answer      VARCHAR(20) NOT NULL,
    notes       TEXT,
    updated_at  DATETIME(6) NOT NULL,
    CONSTRAINT uq_category_usage UNIQUE (business_id, category_id),
    CONSTRAINT fk_cu_business FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE CASCADE,
    CONSTRAINT fk_cu_category FOREIGN KEY (category_id) REFERENCES product_categories (id)
);

-- One row per brand + flavor they currently use: "Monin, Vanilla".
CREATE TABLE product_usages (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    business_id   BIGINT       NOT NULL,
    category_id   BIGINT       NOT NULL,
    brand_id      BIGINT,
    flavor_id     BIGINT,
    product_name  VARCHAR(160),
    quantity      VARCHAR(80),
    frequency     VARCHAR(80),
    notes         TEXT,
    created_by_id BIGINT       NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    CONSTRAINT fk_pu_business FOREIGN KEY (business_id)   REFERENCES businesses (id) ON DELETE CASCADE,
    CONSTRAINT fk_pu_category FOREIGN KEY (category_id)   REFERENCES product_categories (id),
    CONSTRAINT fk_pu_brand    FOREIGN KEY (brand_id)      REFERENCES brands (id),
    CONSTRAINT fk_pu_flavor   FOREIGN KEY (flavor_id)     REFERENCES flavors (id),
    CONSTRAINT fk_pu_creator  FOREIGN KEY (created_by_id) REFERENCES users (id)
);
CREATE INDEX idx_pu_business ON product_usages (business_id);
CREATE INDEX idx_pu_brand    ON product_usages (brand_id);
CREATE INDEX idx_pu_flavor   ON product_usages (flavor_id);

-- Flavors or products they would like, including "cannot get passion fruit as puree".
CREATE TABLE interests (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    business_id   BIGINT      NOT NULL,
    flavor_id     BIGINT,
    product_id    BIGINT,
    status        VARCHAR(30) NOT NULL,
    reason        VARCHAR(30) NOT NULL,
    -- What they said after tasting a sample: "grenadine very good, the rest normal".
    feedback      VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    notes         TEXT,
    created_by_id BIGINT      NOT NULL,
    created_at    DATETIME(6) NOT NULL,
    updated_at    DATETIME(6) NOT NULL,
    CONSTRAINT fk_int_business FOREIGN KEY (business_id)   REFERENCES businesses (id) ON DELETE CASCADE,
    CONSTRAINT fk_int_flavor   FOREIGN KEY (flavor_id)     REFERENCES flavors (id),
    CONSTRAINT fk_int_product  FOREIGN KEY (product_id)    REFERENCES products (id),
    CONSTRAINT fk_int_creator  FOREIGN KEY (created_by_id) REFERENCES users (id)
);
CREATE INDEX idx_int_business ON interests (business_id);
CREATE INDEX idx_int_flavor   ON interests (flavor_id);

-- ---------------------------------------------------------------- work

-- Something that happened: a call, a visit, a meeting that took place, samples sent.
CREATE TABLE activities (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    business_id BIGINT      NOT NULL,
    contact_id  BIGINT,
    user_id     BIGINT      NOT NULL,
    type        VARCHAR(20) NOT NULL,
    result      VARCHAR(30) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    notes       TEXT,
    -- Came from the old spreadsheet: type, result and date were guessed from free text.
    imported    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  DATETIME(6) NOT NULL,
    CONSTRAINT fk_act_business FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE CASCADE,
    CONSTRAINT fk_act_contact  FOREIGN KEY (contact_id)  REFERENCES contacts (id) ON DELETE SET NULL,
    CONSTRAINT fk_act_user     FOREIGN KEY (user_id)     REFERENCES users (id)
);
CREATE INDEX idx_act_business ON activities (business_id, occurred_at);
CREATE INDEX idx_act_user     ON activities (user_id, occurred_at);

-- Something that should happen: a meeting, a call back, sending samples. The calendar is these rows.
CREATE TABLE tasks (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    business_id     BIGINT,
    contact_id      BIGINT,
    assigned_to_id  BIGINT       NOT NULL,
    created_by_id   BIGINT       NOT NULL,
    type            VARCHAR(20)  NOT NULL,
    title           VARCHAR(200),
    due_at          DATETIME(6)  NOT NULL,
    end_at          DATETIME(6),
    all_day         BOOLEAN      NOT NULL DEFAULT FALSE,
    location        VARCHAR(200),
    priority        VARCHAR(10)  NOT NULL,
    status          VARCHAR(12)  NOT NULL,
    notes           TEXT,
    completed_at    DATETIME(6),
    completed_by_id BIGINT,
    activity_id     BIGINT,
    created_at      DATETIME(6)  NOT NULL,
    CONSTRAINT fk_task_business  FOREIGN KEY (business_id)     REFERENCES businesses (id) ON DELETE CASCADE,
    CONSTRAINT fk_task_contact   FOREIGN KEY (contact_id)      REFERENCES contacts (id) ON DELETE SET NULL,
    CONSTRAINT fk_task_assigned  FOREIGN KEY (assigned_to_id)  REFERENCES users (id),
    CONSTRAINT fk_task_creator   FOREIGN KEY (created_by_id)   REFERENCES users (id),
    CONSTRAINT fk_task_completer FOREIGN KEY (completed_by_id) REFERENCES users (id),
    CONSTRAINT fk_task_activity  FOREIGN KEY (activity_id)     REFERENCES activities (id) ON DELETE SET NULL
);
CREATE INDEX idx_task_due      ON tasks (status, due_at);
CREATE INDEX idx_task_assigned ON tasks (assigned_to_id, status, due_at);
CREATE INDEX idx_task_business ON tasks (business_id);

CREATE TABLE comments (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    business_id BIGINT      NOT NULL,
    activity_id BIGINT,
    author_id   BIGINT      NOT NULL,
    body        TEXT        NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    CONSTRAINT fk_com_business FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE CASCADE,
    CONSTRAINT fk_com_activity FOREIGN KEY (activity_id) REFERENCES activities (id) ON DELETE SET NULL,
    CONSTRAINT fk_com_author   FOREIGN KEY (author_id)   REFERENCES users (id)
);
CREATE INDEX idx_com_business ON comments (business_id);

CREATE TABLE purchases (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    business_id   BIGINT        NOT NULL,
    user_id       BIGINT        NOT NULL,
    purchase_date DATE          NOT NULL,
    total         DECIMAL(12,2) NOT NULL,
    notes         TEXT,
    created_at    DATETIME(6)   NOT NULL,
    CONSTRAINT fk_pur_business FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE CASCADE,
    CONSTRAINT fk_pur_user     FOREIGN KEY (user_id)     REFERENCES users (id)
);
CREATE INDEX idx_pur_business ON purchases (business_id, purchase_date);
CREATE INDEX idx_pur_date     ON purchases (purchase_date);

CREATE TABLE purchase_items (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    purchase_id BIGINT        NOT NULL,
    product_id  BIGINT,
    description VARCHAR(200)  NOT NULL,
    quantity    DECIMAL(10,2) NOT NULL,
    unit_price  DECIMAL(10,2) NOT NULL,
    line_total  DECIMAL(12,2) NOT NULL,
    CONSTRAINT fk_pi_purchase FOREIGN KEY (purchase_id) REFERENCES purchases (id) ON DELETE CASCADE,
    CONSTRAINT fk_pi_product  FOREIGN KEY (product_id)  REFERENCES products (id)
);

-- ---------------------------------------------------------------- history

CREATE TABLE status_changes (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    business_id BIGINT       NOT NULL,
    from_status VARCHAR(30),
    to_status   VARCHAR(30)  NOT NULL,
    user_id     BIGINT       NOT NULL,
    changed_at  DATETIME(6)  NOT NULL,
    note        VARCHAR(255),
    CONSTRAINT fk_sc_business FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE CASCADE,
    CONSTRAINT fk_sc_user     FOREIGN KEY (user_id)     REFERENCES users (id)
);
CREATE INDEX idx_sc_business ON status_changes (business_id, changed_at);
CREATE INDEX idx_sc_changed  ON status_changes (changed_at);

-- Who changed what. Summaries are short human sentences; not a full before/after diff.
CREATE TABLE audit_entries (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    business_id BIGINT,
    entity      VARCHAR(40)  NOT NULL,
    entity_id   BIGINT,
    action      VARCHAR(40)  NOT NULL,
    summary     VARCHAR(500),
    user_id     BIGINT,
    created_at  DATETIME(6)  NOT NULL,
    CONSTRAINT fk_audit_user FOREIGN KEY (user_id) REFERENCES users (id)
);
CREATE INDEX idx_audit_business ON audit_entries (business_id, created_at);
CREATE INDEX idx_audit_created  ON audit_entries (created_at);

-- The notes-app replacement: a line typed in five seconds ("myata - call after 3, ask if the
-- bartender tasted"), attached to a business and turned into a task later, or never.
CREATE TABLE quick_notes (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT      NOT NULL,
    body        TEXT        NOT NULL,
    business_id BIGINT,
    remind_at   DATETIME(6),
    done        BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    CONSTRAINT fk_qn_user     FOREIGN KEY (user_id)     REFERENCES users (id),
    CONSTRAINT fk_qn_business FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE SET NULL
);
CREATE INDEX idx_qn_user ON quick_notes (user_id, done, created_at);

CREATE TABLE settings (
    setting_key   VARCHAR(60)  NOT NULL PRIMARY KEY,
    setting_value VARCHAR(255) NOT NULL
);
