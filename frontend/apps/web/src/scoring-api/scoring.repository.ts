import type {
    GameWriteInput,
    RecordedGame,
    TableConfirmation,
    TableRef,
    TeamTable,
} from '@/domain/game.domain.ts';
import {
    recordedGameDtoSchema,
    recordedGameListDtoSchema,
    tableConfirmationDtoSchema,
    teamTableDtoSchema,
} from '@/scoring-api/dtos/scoring.dto.ts';
import {
    confirmRequestOf,
    gameWriteInputToDto,
    recordedGameDtoToDomain,
    tableConfirmationDtoToDomain,
    teamTableDtoToDomain,
} from '@/scoring-api/mappers/scoring.mapper.ts';
import { apiClient } from '@/shared/api/api-client.ts';

const tablePath = ({ tournamentId, roundNumber, tableNumber }: TableRef) =>
    `/api/team/tournaments/${tournamentId}/rounds/${roundNumber}/tables/${tableNumber}`;

const gamesPath = (table: TableRef) => `${tablePath(table)}/games`;

/**
 * The only layer that knows the team scoring endpoints and wire DTOs. Reads parse
 * the response with zod and map it to domain types; the write payload carries raw
 * facts only (ADR 0004) — the server recomputes and returns the authoritative game.
 */

/** `GET /api/team/me/current-table` — the team's in-progress round table. */
async function currentTable(): Promise<TeamTable> {
    const { data } = await apiClient.get<unknown>('/api/team/me/current-table');
    return teamTableDtoToDomain(teamTableDtoSchema.parse(data));
}

/** The games already entered at the table (absent game number = not entered). */
async function games(table: TableRef): Promise<RecordedGame[]> {
    const { data } = await apiClient.get<unknown>(gamesPath(table));
    return recordedGameListDtoSchema.parse(data).map(recordedGameDtoToDomain);
}

/** `POST …/games` — creates a game; `GAME_ALREADY_EXISTS` when the slot is taken. */
async function create(table: TableRef, input: GameWriteInput): Promise<RecordedGame> {
    const { data } = await apiClient.post<unknown>(gamesPath(table), gameWriteInputToDto(input));
    return recordedGameDtoToDomain(recordedGameDtoSchema.parse(data));
}

/** `PUT …/games/{gameNumber}` — explicit edit; carries the optimistic-lock version. */
async function update(table: TableRef, input: GameWriteInput): Promise<RecordedGame> {
    const { data } = await apiClient.put<unknown>(
        `${gamesPath(table)}/${input.gameNumber}`,
        gameWriteInputToDto(input),
    );
    return recordedGameDtoToDomain(recordedGameDtoSchema.parse(data));
}

/**
 * `PUT …/confirmation` — confirms the table for the session's team. Carries the
 * [games] as the team saw them; `GAME_CONCURRENT_MODIFICATION` when any changed.
 */
async function confirm(
    table: TableRef,
    games: readonly RecordedGame[],
): Promise<TableConfirmation> {
    const { data } = await apiClient.put<unknown>(
        `${tablePath(table)}/confirmation`,
        confirmRequestOf(games),
    );
    return tableConfirmationDtoToDomain(tableConfirmationDtoSchema.parse(data));
}

/** `DELETE …/confirmation` — retracts the team's confirmation; `TABLE_COMPLETED` once final. */
async function retract(table: TableRef): Promise<TableConfirmation> {
    const { data } = await apiClient.delete<unknown>(`${tablePath(table)}/confirmation`);
    return tableConfirmationDtoToDomain(tableConfirmationDtoSchema.parse(data));
}

export const ScoringRepository = {
    currentTable,
    games,
    create,
    update,
    confirm,
    retract,
};
