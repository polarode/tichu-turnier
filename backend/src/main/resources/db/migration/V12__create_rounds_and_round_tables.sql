-- Rounds and tables (issue #53). A round is created when the tournament starts
-- (round 1) and later by the round-advance flow. The pairing's tables are stored
-- as child rows; the team ids are stored as values, not foreign keys, so a pairing
-- is an immutable historical fact that does not depend on later team deletions.
--
-- Deleting a tournament removes its rounds and tables via cascade, matching the
-- contract documented on `TournamentRepository.delete`.
--
-- (tournament_id, round_number) is unique: a tournament can hold at most one round
-- per number, and (round_id, table_number) is unique within a round.

CREATE TABLE rounds (
    id            VARCHAR(36) PRIMARY KEY,
    tournament_id VARCHAR(36) NOT NULL REFERENCES tournaments (id) ON DELETE CASCADE,
    round_number  INT NOT NULL,
    CONSTRAINT uq_rounds_tournament_number UNIQUE (tournament_id, round_number)
);

CREATE TABLE round_tables (
    id            BIGSERIAL PRIMARY KEY,
    round_id      VARCHAR(36) NOT NULL REFERENCES rounds (id) ON DELETE CASCADE,
    tournament_id VARCHAR(36) NOT NULL REFERENCES tournaments (id) ON DELETE CASCADE,
    table_number  INT NOT NULL,
    home_team_id  VARCHAR(36) NOT NULL,
    away_team_id  VARCHAR(36) NOT NULL,
    CONSTRAINT uq_round_tables_round_table UNIQUE (round_id, table_number)
);

CREATE INDEX ix_round_tables_tournament ON round_tables (tournament_id);
