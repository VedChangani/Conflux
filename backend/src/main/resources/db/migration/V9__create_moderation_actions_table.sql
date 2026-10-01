-- Append-only audit trail of administrative trust & safety actions. A row is written in the
-- same transaction as the action it records, and only when that action changed something.
-- Like reports, the target is (target_type, target_id) without a foreign key. report_id is
-- set for report reviews only. Enum values are stored as VARCHAR. Timestamps are in UTC.
CREATE TABLE moderation_actions (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    actor_id    BIGINT        NOT NULL,
    action_type VARCHAR(30)   NOT NULL,
    target_type VARCHAR(20)   NOT NULL,
    target_id   BIGINT        NOT NULL,
    report_id   BIGINT,
    note        VARCHAR(1000),
    created_at  DATETIME(6)   NOT NULL,
    CONSTRAINT pk_moderation_actions PRIMARY KEY (id)
);

-- Actions by one administrator, newest first; also backs the actor foreign key.
CREATE INDEX idx_moderation_actions_actor_created ON moderation_actions (actor_id, created_at);

-- History of one target, newest first.
CREATE INDEX idx_moderation_actions_target_created ON moderation_actions (target_type, target_id, created_at);

-- Actions linked to one report; also backs the report foreign key.
CREATE INDEX idx_moderation_actions_report ON moderation_actions (report_id);

ALTER TABLE moderation_actions
    ADD CONSTRAINT fk_moderation_actions_actor FOREIGN KEY (actor_id) REFERENCES users (id) ON DELETE RESTRICT;

ALTER TABLE moderation_actions
    ADD CONSTRAINT fk_moderation_actions_report FOREIGN KEY (report_id) REFERENCES reports (id) ON DELETE RESTRICT;
