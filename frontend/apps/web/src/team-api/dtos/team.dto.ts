import { z } from 'zod';

import { FORFEIT_COMPENSATIONS } from '@/vocabulary/forfeit-compensation.enum.ts';
import { WITHDRAWAL_REASONS } from '@/vocabulary/withdrawal-reason.enum.ts';

/**
 * zod schemas describing the team/player wire format. A team response always carries
 * exactly two players, so the tuple schema rejects a malformed payload at the boundary.
 */
export const playerDtoSchema = z.object({
    id: z.string(),
    teamId: z.string(),
    name: z.string(),
});

/** A team's Withdrawal (ADR 0011): reason, compensation, the round it happened in and when. */
export const withdrawalDtoSchema = z.object({
    reason: z.enum(WITHDRAWAL_REASONS),
    compensation: z.enum(FORFEIT_COMPENSATIONS),
    roundNumber: z.number().int(),
    /** Backend `Instant` serialized as ISO-8601 UTC. */
    withdrawnAt: z.string().datetime({ offset: true }),
});

export const teamDtoSchema = z.object({
    id: z.string(),
    tournamentId: z.string(),
    name: z.string(),
    players: z.tuple([playerDtoSchema, playerDtoSchema]),
    /** Null while the team plays on. */
    withdrawal: withdrawalDtoSchema.nullable(),
});

export const teamListDtoSchema = z.array(teamDtoSchema);

export type PlayerDto = z.infer<typeof playerDtoSchema>;
export type TeamDto = z.infer<typeof teamDtoSchema>;
export type WithdrawalDto = z.infer<typeof withdrawalDtoSchema>;

/** The withdrawal request payload. Mirrors the backend `TeamWithdrawalRequestDto`. */
export interface WithdrawalRequestDto {
    reason: WithdrawalDto['reason'];
    compensation: WithdrawalDto['compensation'];
}

/**
 * The write payload for create and update: a team name plus the two players' names.
 * Mirrors the backend `TeamWriteRequest`.
 */
export interface TeamWriteRequestDto {
    name: string;
    playerOneName: string;
    playerTwoName: string;
}
