import type { RecordedGame, ScheduledGame, SeatedTeams, TableConfirmation } from '@/domain/game.domain.ts';

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
    /** The table status and which teams confirmed (ADR 0006). */
    confirmation: TableConfirmation;
    /** Games played at this table per round (4 at a normal table, 6 at the trio table). */
    gameCount: number;
    schedule: readonly ScheduledGame[];
    /** Games entered so far, ordered by game number; a missing number is not yet entered. */
    games: readonly RecordedGame[];
}

/** One iteration of the tournament: every team plays exactly one match. */
export interface Round {
    /** 1-based round number within the tournament; round 1 is the first round. */
    number: number;
    tables: readonly TableOverview[];
}
