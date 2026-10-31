import { z } from 'zod';

/**
 * zod schemas for the team scoring wire format (S5c, #101). Every response is
 * parsed at the boundary, so a malformed payload is rejected before it reaches a
 * mapper. The write payloads are plain interfaces — they are sent, not parsed.
 */

export const tichuCallDtoSchema = z.enum(['NONE', 'SMALL', 'GRAND']);

const teamTablePlayerDtoSchema = z.object({
    playerId: z.string(),
    name: z.string(),
});

const teamTableTeamDtoSchema = z.object({
    teamId: z.string(),
    teamName: z.string(),
    players: z.tuple([teamTablePlayerDtoSchema, teamTablePlayerDtoSchema]),
});

/** `GET /api/team/me/current-table`. */
export const teamTableDtoSchema = z.object({
    tournamentId: z.string(),
    roundNumber: z.number(),
    tableNumber: z.number(),
    locked: z.boolean(),
    allowGrandTichu: z.boolean(),
    teams: z.tuple([teamTableTeamDtoSchema, teamTableTeamDtoSchema]),
});

const recordedPlayerDtoSchema = z.object({
    playerId: z.string(),
    position: z.number().nullable(),
    call: tichuCallDtoSchema,
    bombs: z.number(),
    tichuSuccess: z.boolean(),
});

const recordedTeamDtoSchema = z.object({
    teamId: z.string(),
    baseScore: z.number(),
    players: z.tuple([recordedPlayerDtoSchema, recordedPlayerDtoSchema]),
});

const gameOutcomeDtoSchema = z.object({
    tichuSuccesses: z.array(z.string()),
    doubleWinTeam: z.string().nullable(),
    teamPoints: z.record(z.string(), z.number()),
    victoryPoints: z.record(z.string(), z.number()),
});

/** The server-authoritative game, returned by POST/PUT and the games list. */
export const recordedGameDtoSchema = z.object({
    gameNumber: z.number(),
    version: z.number(),
    beschiss: z.boolean(),
    teams: z.tuple([recordedTeamDtoSchema, recordedTeamDtoSchema]),
    outcome: gameOutcomeDtoSchema,
});

export const recordedGameListDtoSchema = z.array(recordedGameDtoSchema);

export type TichuCallDto = z.infer<typeof tichuCallDtoSchema>;
export type TeamTableDto = z.infer<typeof teamTableDtoSchema>;
export type RecordedGameDto = z.infer<typeof recordedGameDtoSchema>;

/** Write payloads (sent, not parsed) — mirror the backend `GameRequestDto`. */
export interface PlayerResultRequestDto {
    playerId: string;
    position: number | null;
    call: TichuCallDto;
    bombs: number;
}

export interface TeamResultRequestDto {
    teamId: string;
    baseScore: number;
    players: [PlayerResultRequestDto, PlayerResultRequestDto];
}

export interface GameRequestDto {
    gameNumber: number;
    beschiss: boolean;
    version: number | null;
    teams: [TeamResultRequestDto, TeamResultRequestDto];
}
