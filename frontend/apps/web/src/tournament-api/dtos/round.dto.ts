import { z } from 'zod';

import {
    recordedGameDtoSchema,
    scheduledGameDtoSchema,
    seatedSchema,
    tableConfirmationDtoSchema,
} from '@/scoring-api/dtos/scoring.dto.ts';
import { tournamentDtoSchema } from '@/tournament-api/dtos/tournament.dto.ts';

/**
 * zod schemas describing the round wire format, as returned by the start endpoint
 * (inline round 1) and the rounds read endpoint. Every response is parsed through
 * these at the repository boundary.
 */
export const tableOverviewDtoSchema = z.object({
    tableNumber: z.number(),
    /** The seated teams in seat order: two, or three at the trio table. */
    teamIds: seatedSchema(z.string()),
    trio: z.boolean(),
    /** The table status and who confirmed (ADR 0006), as the team's current table carries it. */
    ...tableConfirmationDtoSchema.shape,
    /** Games played at this table per round — the denominator of "x/n entered". */
    gameCount: z.number(),
    schedule: z.array(scheduledGameDtoSchema),
    /** Games entered so far, ordered by game number (the shared recorded-game shape). */
    games: z.array(recordedGameDtoSchema),
});

export type TableOverviewDto = z.infer<typeof tableOverviewDtoSchema>;

export const roundDtoSchema = z.object({
    roundNumber: z.number(),
    tables: z.array(tableOverviewDtoSchema),
});

export type RoundDto = z.infer<typeof roundDtoSchema>;

export const roundListDtoSchema = z.array(roundDtoSchema);

/** The start response: the activated tournament plus the freshly created round 1. */
export const startedTournamentDtoSchema = z.object({
    tournament: tournamentDtoSchema,
    round: roundDtoSchema,
});

export type StartedTournamentDto = z.infer<typeof startedTournamentDtoSchema>;
