import { z } from 'zod';

import { standingsEntryDtoSchema } from '@/tournament-api/dtos/standings.dto.ts';
import { TOURNAMENT_STATUSES } from '@/vocabulary/tournament-status.enum.ts';

/**
 * zod schemas describing the public wire formats (`GET /api/public/tournaments` and
 * `GET /api/public/tournaments/{id}`). Public facts only; parsed at the repository boundary.
 * Table totals and the final ranking are only sent once the tournament is Published.
 */
/** One entry of `GET /api/public/tournaments` (public facts only). */
export const publicTournamentDtoSchema = z.object({
    id: z.string(),
    name: z.string(),
    status: z.enum(TOURNAMENT_STATUSES),
    teamCount: z.number().int(),
});

export const publicTournamentListDtoSchema = z.array(publicTournamentDtoSchema);

export type PublicTournamentDto = z.infer<typeof publicTournamentDtoSchema>;

export const publicPlayerDtoSchema = z.object({
    id: z.string(),
    name: z.string(),
});

export type PublicPlayerDto = z.infer<typeof publicPlayerDtoSchema>;

export const publicTeamDtoSchema = z.object({
    id: z.string(),
    name: z.string(),
    /** The team's players in the order the server lists them. */
    players: z.array(publicPlayerDtoSchema),
    /** The team's Table Totals at this table; present only once the tournament is Published. */
    totals: z
        .object({
            victoryPoints: z.number().int(),
            tichuPoints: z.number().int(),
        })
        .optional(),
});

export type PublicTeamDto = z.infer<typeof publicTeamDtoSchema>;

export const publicTableDtoSchema = z.object({
    tableNumber: z.number().int(),
    trio: z.boolean(),
    /** The seated teams in seat order: two, or three at the trio table. */
    teams: z.array(publicTeamDtoSchema),
});

export type PublicTableDto = z.infer<typeof publicTableDtoSchema>;

export const publicRoundDtoSchema = z.object({
    number: z.number().int(),
    tables: z.array(publicTableDtoSchema),
});

export type PublicRoundDto = z.infer<typeof publicRoundDtoSchema>;

export const publicTournamentDetailDtoSchema = z.object({
    id: z.string(),
    name: z.string(),
    status: z.enum(TOURNAMENT_STATUSES),
    rounds: z.array(publicRoundDtoSchema),
    /** The Final Ranking; present only once the tournament is Published. */
    finalRanking: z.array(standingsEntryDtoSchema).optional(),
});

export type PublicTournamentDetailDto = z.infer<typeof publicTournamentDetailDtoSchema>;
