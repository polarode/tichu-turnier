-- Teams and players (issue #7). Players are tournament-scoped — there is no global
-- player table — so both tables carry `tournament_id` and every lookup stays within
-- one tournament. Deleting a tournament removes its teams and players via cascade,
-- the contract already documented on `TournamentRepository.delete`.
--
-- Uniqueness is per tournament and case-insensitive (`lower(name)`), matching how
-- the application compares names for duplicates:
--   - a team name may be used only once per tournament;
--   - a player name may be used only once per tournament, which is what keeps a
--     player off two teams — each player row belongs to exactly one team.
-- Expression-based unique rules are separate indexes, not inline table constraints.

CREATE TABLE teams (
    id            VARCHAR(36) PRIMARY KEY,
    tournament_id VARCHAR(36) NOT NULL REFERENCES tournaments (id) ON DELETE CASCADE,
    name          VARCHAR(255) NOT NULL,
    CONSTRAINT uq_teams_id_tournament UNIQUE (id, tournament_id)
);

CREATE UNIQUE INDEX uq_teams_tournament_name ON teams (tournament_id, lower(name));

CREATE TABLE players (
    id            VARCHAR(36) PRIMARY KEY,
    tournament_id VARCHAR(36) NOT NULL REFERENCES tournaments (id) ON DELETE CASCADE,
    team_id       VARCHAR(36) NOT NULL REFERENCES teams (id) ON DELETE CASCADE,
    name          VARCHAR(255) NOT NULL
);

CREATE UNIQUE INDEX uq_players_tournament_name ON players (tournament_id, lower(name));

-- Keep the two scoping dimensions consistent: a player's tournament must always be
-- the tournament of the team it sits on. Without this a buggy write could attach a
-- player to a team of another tournament, silently escaping the scoping rules.
ALTER TABLE players
    ADD CONSTRAINT fk_players_team_tournament
    FOREIGN KEY (team_id, tournament_id) REFERENCES teams (id, tournament_id) ON DELETE CASCADE;
