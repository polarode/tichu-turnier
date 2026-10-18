-- Ticket 005: the tournament domain replaces the template's Task guinea pig.
-- The tasks table (V1, owner column added in V3) is retired; existing rows were
-- guinea-pig data with no meaning, so the table is simply dropped. V1/V3 stay in
-- the history so already-migrated databases validate cleanly.

CREATE TABLE tournaments (
    id          VARCHAR(36) PRIMARY KEY,
    owner_id    VARCHAR(36) NOT NULL REFERENCES admin_users (id) ON DELETE CASCADE,
    name        VARCHAR(255) NOT NULL,
    description TEXT         NOT NULL,
    max_teams   INTEGER      NOT NULL CHECK (max_teams > 0),
    settings    JSONB        NOT NULL,
    status      VARCHAR(32)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_tournaments_owner_id ON tournaments (owner_id);

DROP TABLE IF EXISTS tasks;
