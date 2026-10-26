import { z } from 'zod';

import { tournamentDtoSchema } from '@/tournament-api/dtos/tournament.dto.ts';

/**
 * zod schemas describing the round wire format, as returned by the start endpoint
 * (inline round 1) and the rounds read endpoint. Every response is parsed through
 * these at the repository boundary.
 */
export const tableAssignmentDtoSchema = z.object({
    tableNumber: z.number(),
    homeTeamId: z.string(),
    awayTeamId: z.string(),
});

export type TableAssignmentDto = z.infer<typeof tableAssignmentDtoSchema>;

export const roundDtoSchema = z.object({
    roundNumber: z.number(),
    tables: z.array(tableAssignmentDtoSchema),
});

export type RoundDto = z.infer<typeof roundDtoSchema>;

export const roundListDtoSchema = z.array(roundDtoSchema);

/** The start response: the activated tournament plus the freshly created round 1. */
export const startedTournamentDtoSchema = z.object({
    tournament: tournamentDtoSchema,
    round: roundDtoSchema,
});

export type StartedTournamentDto = z.infer<typeof startedTournamentDtoSchema>;
