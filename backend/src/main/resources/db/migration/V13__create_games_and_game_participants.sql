-- Games and per-player participants (issue #100, S5b). A game row is created
-- lazily on the first submit for its slot — the pairing flow never pre-creates
-- games. The natural key is (tournament_id, round_number, table_number,
-- game_number): round/table numbers are stable, while the round repository
-- re-saves round_tables wholesale, so games must not reference a round table's
-- surrogate id. The team and player ids are foreign keys — teams and players of
-- a started tournament cannot be deleted, so the integrity is real.
--
-- The base scores are stored as raw facts (ADR 0004); everything derivable
-- (bonuses, totals, victory points) is computed on read by the scoring engine.
-- Version is the optimistic-locking counter (starts at 1, increments on every
-- successful update).
--
-- Deleting a tournament removes its games via cascade, matching the contract
-- documented on `TournamentRepository.delete`: every FK that traces back to the
-- tournament (teams, players) cascades, so removing a team or player removes the
-- games and participants that record them — a result whose participants are gone
-- is meaningless. Participants also cascade with their game row.
--
-- The per-round lock state (S5's minimal confirm surface, S6 builds the
-- workflow on it): one boolean on the round row — the confirm event is
-- per-round, so games are editable exactly while their round is unlocked.

ALTER TABLE rounds
    ADD COLUMN locked BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE games (
    id               BIGSERIAL PRIMARY KEY,
    tournament_id    VARCHAR(36) NOT NULL REFERENCES tournaments (id) ON DELETE CASCADE,
    round_number     INT NOT NULL,
    table_number     INT NOT NULL,
    game_number      INT NOT NULL,
    team1_id         VARCHAR(36) NOT NULL REFERENCES teams (id) ON DELETE CASCADE,
    team2_id         VARCHAR(36) NOT NULL REFERENCES teams (id) ON DELETE CASCADE,
    base_score_team1 INT NOT NULL,
    base_score_team2 INT NOT NULL,
    beschiss         BOOLEAN NOT NULL DEFAULT FALSE,
    version          INT NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_games_natural_key UNIQUE (tournament_id, round_number, table_number, game_number),
    CONSTRAINT ck_games_teams_distinct CHECK (team1_id <> team2_id)
);

CREATE INDEX ix_games_tournament ON games (tournament_id);

CREATE TABLE game_participants (
    id         BIGSERIAL PRIMARY KEY,
    game_id    BIGINT NOT NULL REFERENCES games (id) ON DELETE CASCADE,
    player_id  VARCHAR(36) NOT NULL REFERENCES players (id) ON DELETE CASCADE,
    team_id    VARCHAR(36) NOT NULL REFERENCES teams (id) ON DELETE CASCADE,
    position   INT NULL,
    tichu_call VARCHAR(16) NOT NULL,
    bomb_count INT NOT NULL
);

CREATE INDEX ix_game_participants_game ON game_participants (game_id);
