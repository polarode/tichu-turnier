import type { Player } from '@/domain/player.domain.ts';
import type { ForfeitCompensation } from '@/vocabulary/forfeit-compensation.enum.ts';
import type { WithdrawalReason } from '@/vocabulary/withdrawal-reason.enum.ts';

/**
 * A team of a tournament: a name plus exactly two distinct players. The tuple type
 * mirrors the backend invariant that a team always has two players, so the UI never
 * has to handle a missing one.
 */
export interface Team {
    id: string;
    tournamentId: string;
    name: string;
    players: readonly [Player, Player];
    /** The team's Withdrawal (ADR 0011); null while it plays on. */
    withdrawal: TeamWithdrawal | null;
}

/**
 * A team that left the tournament for good (GLOSSARY.md: Withdrawal). The games it already
 * played stand; the games it still owed at its current table became Forfeit Games.
 */
export interface TeamWithdrawal {
    reason: WithdrawalReason;
    compensation: ForfeitCompensation;
    /** The round the team was in when it withdrew. */
    roundNumber: number;
    withdrawnAt: Date;
}

/** The organizer's choice when withdrawing a team: why, and what its owed games are worth. */
export interface WithdrawalInput {
    reason: WithdrawalReason;
    compensation: ForfeitCompensation;
}

/**
 * The editable fields of a team, as forms produce them. A domain-level input so the
 * layers above the repository never see the wire DTO.
 */
export interface TeamWriteInput {
    name: string;
    playerOneName: string;
    playerTwoName: string;
}
