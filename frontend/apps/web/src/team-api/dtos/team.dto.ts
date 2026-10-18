import { z } from 'zod';

/**
 * zod schemas describing the team/player wire format. A team response always carries
 * exactly two players, so the tuple schema rejects a malformed payload at the boundary.
 */
export const playerDtoSchema = z.object({
    id: z.string(),
    teamId: z.string(),
    name: z.string(),
});

export const teamDtoSchema = z.object({
    id: z.string(),
    tournamentId: z.string(),
    name: z.string(),
    players: z.tuple([playerDtoSchema, playerDtoSchema]),
});

export const teamListDtoSchema = z.array(teamDtoSchema);

export type PlayerDto = z.infer<typeof playerDtoSchema>;
export type TeamDto = z.infer<typeof teamDtoSchema>;

/**
 * The write payload for create and update: a team name plus the two players' names.
 * Mirrors the backend `TeamWriteRequest`.
 */
export interface TeamWriteRequestDto {
    name: string;
    playerOneName: string;
    playerTwoName: string;
}
