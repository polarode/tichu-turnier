import { z } from 'zod';

import {
    doubleWinBonusDtoSchema,
    grandTichuBonusDtoSchema,
} from '@/tournament-api/dtos/tournament.dto.ts';
import { TABLE_STATUSES } from '@/vocabulary/table-status.enum.ts';

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

/** A table's seated teams in seat order: two, or three at the trio table — never more. */
export function seatedSchema<T extends z.ZodType>(seat: T) {
    return z.union([z.tuple([seat, seat]), z.tuple([seat, seat, seat])]);
}

const teamTableTeamDtoSchema = z.object({
    teamId: z.string(),
    teamName: z.string(),
    players: z.tuple([teamTablePlayerDtoSchema, teamTablePlayerDtoSchema]),
});

/** One game of a table's schedule: who plays it. The server is the single source of the trio schedule. */
export const scheduledGameDtoSchema = z.object({
    gameNumber: z.number(),
    teamIds: z.tuple([z.string(), z.string()]),
});

/** A table's confirmation state: the confirm/retract response, and part of the reads. */
export const tableConfirmationDtoSchema = z.object({
    status: z.enum(TABLE_STATUSES),
    /** The teams that confirmed, in seat order. */
    confirmedTeamIds: z.array(z.string()),
});

/** `GET /api/team/me/current-table`. */
export const teamTableDtoSchema = z.object({
    tournamentId: z.string(),
    roundNumber: z.number(),
    tableNumber: z.number(),
    ...tableConfirmationDtoSchema.shape,
    grandTichuBonus: grandTichuBonusDtoSchema,
    doubleWinBonus: doubleWinBonusDtoSchema,
    /** The seated teams in seat order: two, or three at the trio table. */
    teams: seatedSchema(teamTableTeamDtoSchema),
    trio: z.boolean(),
    schedule: z.array(scheduledGameDtoSchema),
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

/** The organizer correction stamp (ADR 0010): who corrected the game and the ISO instant. */
const gameCorrectionDtoSchema = z.object({
    correctedBy: z.string(),
    correctedAt: z.string(),
});

/**
 * The server-authoritative game, returned by POST/PUT, the games list, the admin rounds
 * read and the correction PUT. `correction` is always present on the wire, null when the
 * game was never corrected.
 */
export const recordedGameDtoSchema = z.object({
    gameNumber: z.number(),
    version: z.number(),
    beschiss: z.boolean(),
    teams: z.tuple([recordedTeamDtoSchema, recordedTeamDtoSchema]),
    outcome: gameOutcomeDtoSchema,
    correction: gameCorrectionDtoSchema.nullable(),
});

export const recordedGameListDtoSchema = z.array(recordedGameDtoSchema);

export type TichuCallDto = z.infer<typeof tichuCallDtoSchema>;
export type TeamTableDto = z.infer<typeof teamTableDtoSchema>;
export type TableConfirmationDto = z.infer<typeof tableConfirmationDtoSchema>;
export type ScheduledGameDto = z.infer<typeof scheduledGameDtoSchema>;
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

/** `PUT …/confirmation` — the games the team confirms, as it last read them. */
export interface ConfirmRequestDto {
    games: { gameNumber: number; version: number }[];
}
