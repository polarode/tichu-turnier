CREATE TABLE admin_users (
    id            VARCHAR(36)  PRIMARY KEY,
    email         VARCHAR(320) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL
);

CREATE TABLE admin_sessions (
    id            VARCHAR(36) PRIMARY KEY,
    admin_user_id VARCHAR(36) NOT NULL REFERENCES admin_users (id) ON DELETE CASCADE,
    token_hash    VARCHAR(64) NOT NULL UNIQUE,
    created_at    TIMESTAMPTZ NOT NULL,
    expires_at    TIMESTAMPTZ NOT NULL
);
