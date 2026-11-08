import { z } from 'zod';

/** zod schema of the statistics read (organizer, team and public share one body). */
export const teamStatisticsDtoSchema = z.object({
    teamId: z.string(),
    teamName: z.string(),
    successfulSmallTichu: z.number().int().nonnegative(),
    successfulGrandTichu: z.number().int().nonnegative(),
    bombCount: z.number().int().nonnegative(),
    bombsPerGame: z.number().nullable(),
    doubleWins: z.number().int().nonnegative(),
});

export const highlightDtoSchema = z.object({
    metric: z.enum(['SUCCESSFUL_TICHU', 'BOMBS', 'DOUBLE_WINS']),
    value: z.number().int().nonnegative(),
    teamIds: z.array(z.string()),
});

export const trendPointDtoSchema = z.object({
    roundNumber: z.number().int().positive(),
    victoryPoints: z.number().int(),
});

export const teamTrendDtoSchema = z.object({
    teamId: z.string(),
    points: z.array(trendPointDtoSchema),
});

export const statisticsDtoSchema = z.object({
    teams: z.array(teamStatisticsDtoSchema),
    highlights: z.array(highlightDtoSchema),
    trends: z.array(teamTrendDtoSchema),
});

export type TeamStatisticsDto = z.infer<typeof teamStatisticsDtoSchema>;
export type HighlightDto = z.infer<typeof highlightDtoSchema>;
export type TeamTrendDto = z.infer<typeof teamTrendDtoSchema>;
export type TournamentStatisticsDto = z.infer<typeof statisticsDtoSchema>;
