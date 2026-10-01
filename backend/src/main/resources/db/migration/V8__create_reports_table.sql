-- User reports about a user, listing or message, reviewed reactively by administrators.
-- The target is (target_type, target_id) without a foreign key: it can point at different
-- tables, and the application resolves it. Enum values are stored as VARCHAR. Timestamps
-- are stored in UTC.
CREATE TABLE reports (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    reporter_id     BIGINT        NOT NULL,
    target_type     VARCHAR(20)   NOT NULL,
    target_id       BIGINT        NOT NULL,
    reason          VARCHAR(40)   NOT NULL,
    details         VARCHAR(1000),
    status          VARCHAR(20)   NOT NULL,
    reviewed_by     BIGINT,
    reviewed_at     DATETIME(6),
    resolution_note VARCHAR(1000),
    created_at      DATETIME(6)   NOT NULL,
    CONSTRAINT pk_reports PRIMARY KEY (id),
    -- One report per reporter and target; also backs the reporter foreign key.
    CONSTRAINT uk_reports_reporter_target UNIQUE (reporter_id, target_type, target_id)
);

-- Admin queue: WHERE status = ? ORDER BY created_at DESC, id DESC.
CREATE INDEX idx_reports_status_created_id ON reports (status, created_at, id);

-- All reports about one target.
CREATE INDEX idx_reports_target ON reports (target_type, target_id);

ALTER TABLE reports
    ADD CONSTRAINT fk_reports_reporter FOREIGN KEY (reporter_id) REFERENCES users (id) ON DELETE RESTRICT;

ALTER TABLE reports
    ADD CONSTRAINT fk_reports_reviewed_by FOREIGN KEY (reviewed_by) REFERENCES users (id) ON DELETE RESTRICT;
