-- Backups: a zip of everything, kept in the database for a while (retention setting) so other admins can
-- download it too, and a log of who made or downloaded which backup, from where. The log outlives the files.

CREATE TABLE backups (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    file_name     VARCHAR(160) NOT NULL,
    size_bytes    BIGINT       NOT NULL,
    sha256        CHAR(64)     NOT NULL,
    content       LONGBLOB     NOT NULL,
    businesses    INT          NOT NULL,
    projects      INT          NOT NULL,
    created_by_id BIGINT,
    created_at    DATETIME(6)  NOT NULL,
    CONSTRAINT fk_backup_creator FOREIGN KEY (created_by_id) REFERENCES users (id) ON DELETE SET NULL
);
CREATE INDEX idx_backup_created ON backups (created_at);

-- action: CREATED, DOWNLOADED, DELETED (by hand) or EXPIRED (removed after the retention period).
CREATE TABLE backup_events (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    backup_id  BIGINT,
    file_name  VARCHAR(160) NOT NULL,
    action     VARCHAR(20)  NOT NULL,
    user_id    BIGINT,
    username   VARCHAR(60),
    ip         VARCHAR(64),
    user_agent VARCHAR(300),
    created_at DATETIME(6)  NOT NULL,
    CONSTRAINT fk_event_backup FOREIGN KEY (backup_id) REFERENCES backups (id) ON DELETE SET NULL
);
CREATE INDEX idx_backup_event ON backup_events (backup_id, created_at);
CREATE INDEX idx_backup_event_time ON backup_events (action, created_at);
