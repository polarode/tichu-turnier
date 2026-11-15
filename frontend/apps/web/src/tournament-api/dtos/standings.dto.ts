import { z } from 'zod';

import { WITHDRAWAL_REASONS } from '@/vocabulary/withdrawal-reason.enum.ts';

/**
 * zod schema of the standings read (admin, team Final Ranking and public Final Ranking): one
 * line per team, in standings order. `rank` is null for a disqualified team.
 */
export const standingsEntryDtoSchema = z.object({
    rank: z.number().int().positive().nullable(),
    teamId: z.string(),
    teamName: z.string(),
    gamesPlayed: z.number().int(),
    victoryPoints: z.number().int(),
    tichuPoints: z.number().int(),
    /** `DISQUALIFIED` or `VOLUNTARY` once the team withdrew, null while it plays on. */
    withdrawalReason: z.enum(WITHDRAWAL_REASONS).nullable(),
});

export const standingsDtoSchema = z.array(standingsEntryDtoSchema);

export type StandingsEntryDto = z.infer<typeof standingsEntryDtoSchema>;
