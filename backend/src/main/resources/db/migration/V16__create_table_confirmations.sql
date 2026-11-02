-- Per-table confirmation (S6, #105; ADR 0006): one row per team that confirmed
-- the games at its round table. The Table Status is derived from these rows,
-- never stored. Keyed by the table's natural key plus the team, like `games`:
-- round/table numbers are stable, while round_tables is re-saved wholesale.
-- Rows cascade with the tournament and the team, as games do.
--
-- Supersedes S5's per-round lock: games freeze at a table's first confirmation.
ALTER TABLE rounds DROP COLUMN locked;

CREATE TABLE table_confirmations (
    id            BIGSERIAL PRIMARY KEY,
    tournament_id VARCHAR(36) NOT NULL REFERENCES tournaments (id) ON DELETE CASCADE,
    round_number  INT NOT NULL,
    table_number  INT NOT NULL,
    team_id       VARCHAR(36) NOT NULL REFERENCES teams (id) ON DELETE CASCADE,
    confirmed_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_table_confirmations UNIQUE (tournament_id, round_number, table_number, team_id)
);
