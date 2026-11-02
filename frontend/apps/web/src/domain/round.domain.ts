import type { RecordedGame, ScheduledGame, SeatedTeams } from '@/domain/game.domain.ts';

/**
 * One table of a round as the admin follows it: the seating (two teams, or three at
 * the trio table), who plays which game, plus the games entered there so far. Table
 * numbers are the backend's 1-based sequential numbers; the ids reference teams of
 * the same tournament.
 */
export interface TableOverview {
    tableNumber: number;
    teamIds: SeatedTeams<string>;
    trio: boolean;
    /** Games played at this table per round (4 at a normal table, 6 at the trio table). */
    gameCount: number;
    schedule: readonly ScheduledGame[];
    /** Games entered so far, ordered by game number; a missing number is not yet entered. */
    games: readonly RecordedGame[];
}

/** The two teams playing [gameNumber] at [table], per the server schedule; undefined for an unknown game. */
export function playingTeamIds(table: TableOverview, gameNumber: number): ScheduledGame['teamIds'] | undefined {
    return table.schedule.find((scheduled) => scheduled.gameNumber === gameNumber)?.teamIds;
}

/** One iteration of the tournament: every team plays exactly one match. */
export interface Round {
    /** 1-based round number within the tournament; round 1 is the first round. */
    number: number;
    tables: readonly TableOverview[];
}
