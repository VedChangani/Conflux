-- Users of Conflux.
-- email and username are stored already normalized (trimmed, lower-cased) by the
-- application, so the plain unique constraints below enforce case-insensitive uniqueness.
-- role and status hold enum names as strings (no native ENUM columns).
-- Timestamps are stored in UTC.
CREATE TABLE users (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    email         VARCHAR(254)  NOT NULL,
    password_hash VARCHAR(255)  NOT NULL,
    username      VARCHAR(50)   NOT NULL,
    display_name  VARCHAR(100)  NOT NULL,
    bio           VARCHAR(1000),
    location      VARCHAR(100),
    website_url   VARCHAR(500),
    github_url    VARCHAR(500),
    linkedin_url  VARCHAR(500),
    role          VARCHAR(20)   NOT NULL,
    status        VARCHAR(20)   NOT NULL,
    created_at    DATETIME(6)   NOT NULL,
    updated_at    DATETIME(6)   NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_username UNIQUE (username)
);
