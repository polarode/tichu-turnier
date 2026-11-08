-- Player names only need to be unique within a team, not across the tournament.
-- Two teams may share a player name (e.g. two "Anna"s); the application still rejects
-- a name repeated within one team (DuplicateTeamPlayersException). Dropping the
-- tournament-wide index removes the database-level rule that made this impossible.

DROP INDEX uq_players_tournament_name;
