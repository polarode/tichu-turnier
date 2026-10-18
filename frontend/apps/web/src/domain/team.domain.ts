import type { Player } from '@/domain/player.domain.ts';

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
