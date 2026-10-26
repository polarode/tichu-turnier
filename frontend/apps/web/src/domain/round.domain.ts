/**
 * One seating at a table: the two opposing teams of one match. Table numbers are the
 * backend's 1-based sequential numbers; the ids reference teams of the same tournament.
 */
export interface TableAssignment {
    tableNumber: number;
    homeTeamId: string;
    awayTeamId: string;
}

/** One iteration of the tournament: every team plays exactly one match. */
export interface Round {
    /** 1-based round number within the tournament; round 1 is the first round. */
    number: number;
    tables: readonly TableAssignment[];
}
