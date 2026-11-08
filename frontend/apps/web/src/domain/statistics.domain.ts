/** The team statistics a Highlight can lead (mirrors the backend `HighlightMetric`). */
export type HighlightMetric = 'SUCCESSFUL_TICHU' | 'BOMBS' | 'DOUBLE_WINS';

/**
 * One team's Team Statistics (glossary): call successes, bombs and double wins over the
 * games of completed tables. `bombsPerGame` is null while the team has no completed table.
 */
export interface TeamStatistics {
    teamId: string;
    teamName: string;
    successfulSmallTichu: number;
    successfulGrandTichu: number;
    bombCount: number;
    bombsPerGame: number | null;
    doubleWins: number;
}

/** A team named by a Highlight or a Team Trend, resolved to its name so screens need no lookup. */
export interface StatisticsTeam {
    teamId: string;
    teamName: string;
}

/** The team(s) leading one metric; every team tied at `value` is listed. */
export interface Highlight {
    metric: HighlightMetric;
    value: number;
    teams: StatisticsTeam[];
}

/** A team's cumulative Victory Points after one completed round. */
export interface TrendPoint {
    roundNumber: number;
    victoryPoints: number;
}

/** One team's Team Trend: its cumulative Victory Points after each completed round. */
export interface TeamTrend extends StatisticsTeam {
    points: TrendPoint[];
}

/** The statistics of one tournament, as served to the organizer, the team and spectators alike. */
export interface TournamentStatistics {
    teams: TeamStatistics[];
    highlights: Highlight[];
    trends: TeamTrend[];
}
