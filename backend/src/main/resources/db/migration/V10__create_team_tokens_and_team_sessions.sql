-- Phase 5 (#24): team magic-link access — the hashed per-team token and the
-- team sessions exchanged for it.
--
-- team_tokens holds exactly one row per team (team_id is the primary key and a
-- foreign key that cascades, so deleting a team removes its token). Only the
-- SHA-256 digest of the 256-bit token is stored; the raw token exists solely in
-- the link handed to the organizer. `revealed` marks whether the organizer has
-- seen the raw link yet; `revoked_at` ends the link's life immediately.
--
-- team_sessions mirrors admin_sessions: the browser cookie carries the raw
-- token, the database only its digest. Indexed on expires_at for the periodic
-- session sweep (Phase 5).

CREATE TABLE team_tokens (
    team_id    VARCHAR(36) PRIMARY KEY REFERENCES teams (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    revealed   BOOLEAN     NOT NULL DEFAULT FALSE,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE team_sessions (
    id         VARCHAR(36) PRIMARY KEY,
    team_id    VARCHAR(36) NOT NULL REFERENCES teams (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX team_sessions_expires_at_idx ON team_sessions (expires_at);