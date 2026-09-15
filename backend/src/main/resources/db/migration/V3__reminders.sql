-- Reminders that reach phones and laptops (web push) and the phone's own calendar (.ics), and the keys they need.

-- ---------------------------------------------------------------- when to remind

-- Minutes before a task that its assignee is reminded. 0: no reminders unless a task asks for one.
ALTER TABLE users ADD COLUMN reminder_minutes INT NOT NULL DEFAULT 30;
-- Secret part of the personal calendar feed address (/cal/{token}.ics). New token = old address stops working.
ALTER TABLE users ADD COLUMN calendar_token VARCHAR(64);
CREATE UNIQUE INDEX uq_users_calendar_token ON users (calendar_token);

-- NULL: the assignee's default. 0: no reminder for this task. Otherwise minutes before.
ALTER TABLE tasks ADD COLUMN remind_minutes INT;
-- Set once the reminder went out, cleared when the task is moved, so it is sent exactly once per time.
ALTER TABLE tasks ADD COLUMN reminded_at DATETIME(6);
CREATE INDEX idx_task_reminder ON tasks (status, reminded_at, due_at);

ALTER TABLE quick_notes ADD COLUMN reminded_at DATETIME(6);

-- ---------------------------------------------------------------- devices that receive push notifications

-- One row per browser or installed web app that allowed notifications. The push service address is long,
-- so uniqueness is enforced on its SHA-256.
CREATE TABLE push_subscriptions (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT        NOT NULL,
    endpoint        VARCHAR(1000) NOT NULL,
    endpoint_hash   CHAR(64)      NOT NULL,
    p256dh          VARCHAR(200)  NOT NULL,
    auth_secret     VARCHAR(100)  NOT NULL,
    lang            VARCHAR(5),
    user_agent      VARCHAR(300),
    created_at      DATETIME(6)   NOT NULL,
    last_success_at DATETIME(6),
    CONSTRAINT uq_push_endpoint UNIQUE (endpoint_hash),
    CONSTRAINT fk_push_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_push_user ON push_subscriptions (user_id);

-- ---------------------------------------------------------------- keys the application makes for itself

-- The VAPID key pair that signs push messages, created on first use unless VAPID_* variables are set.
CREATE TABLE app_secrets (
    name         VARCHAR(64) NOT NULL PRIMARY KEY,
    secret_value TEXT        NOT NULL,
    created_at   DATETIME(6) NOT NULL
);
