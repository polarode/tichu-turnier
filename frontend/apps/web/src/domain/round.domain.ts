import type { RecordedGame } from '@/domain/game.domain.ts';

/**
 * One table of a round as the admin follows it: the seating (the two opposing teams)
 * plus the games entered there so far. Table numbers are the backend's 1-based
 * sequential numbers; the ids reference teams of the same tournament.
 */
export interface TableOverview {
    tableNumber: number;
    homeTeamId: string;
    awayTeamId: string;
    /** Games played at this table per round (4 for a normal table). */
    gameCount: number;
    /** Games entered so far, ordered by game number; a missing number is not yet entered. */
    games: readonly RecordedGame[];
}

/** One iteration of the tournament: every team plays exactly one match. */
export interface Round {
    /** 1-based round number within the tournament; round 1 is the first round. */
    number: number;
    tables: readonly TableOverview[];
}
