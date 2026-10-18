-- Phase 4 (#23): owner-issued admin invites. An invite is addressed to an email and
-- carries the SHA-256 digest of a high-entropy, single-use token; the raw token is
-- returned to the owner once at issuance and never persisted or listed again.
--
-- status is constrained to the application vocabulary at the database boundary, as with
-- admin_users. spent_at is set together with the account's invited_at when accepted.

CREATE TABLE admin_invites (
    id         VARCHAR(36)  PRIMARY KEY,
    email      VARCHAR(320) NOT NULL,
    token_hash VARCHAR(64)  NOT NULL UNIQUE,
    status     VARCHAR(20)  NOT NULL,
    created_by VARCHAR(36)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    expires_at TIMESTAMPTZ  NOT NULL,
    spent_at   TIMESTAMPTZ,
    CONSTRAINT admin_invites_status_check CHECK (status IN ('PENDING', 'SPENT', 'REVOKED'))
);

CREATE INDEX admin_invites_email_idx ON admin_invites (email);
