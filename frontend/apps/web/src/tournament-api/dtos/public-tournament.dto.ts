import { z } from 'zod';

import { TOURNAMENT_STATUSES } from '@/vocabulary/tournament-status.enum.ts';

/**
 * zod schemas describing the public wire formats (`GET /api/public/tournaments` and
 * `GET /api/public/tournaments/{id}`). Public facts only; parsed at the repository boundary.
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
});

export type PublicTournamentDetailDto = z.infer<typeof publicTournamentDetailDtoSchema>;
