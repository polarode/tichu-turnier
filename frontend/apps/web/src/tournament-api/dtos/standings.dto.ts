import { z } from 'zod';

/** zod schema of the admin standings read: one line per team, in standings order. */
export const standingsEntryDtoSchema = z.object({
    rank: z.number().int().positive(),
    teamId: z.string(),
    teamName: z.string(),
    gamesPlayed: z.number().int(),
    victoryPoints: z.number().int(),
    tichuPoints: z.number().int(),
});

export const standingsDtoSchema = z.array(standingsEntryDtoSchema);

export type StandingsEntryDto = z.infer<typeof standingsEntryDtoSchema>;
