import { tournamentDtoToDomain, tournamentWriteInputToDto } from '@/tournament-api/mappers/tournament.mapper.ts';
import { roundDtoToDomain } from '@/tournament-api/mappers/round.mapper.ts';
import { tournamentDtoSchema, tournamentListDtoSchema } from '@/tournament-api/dtos/tournament.dto.ts';
import { roundDtoSchema, roundListDtoSchema, startedTournamentDtoSchema } from '@/tournament-api/dtos/round.dto.ts';
import { standingsDtoSchema } from '@/tournament-api/dtos/standings.dto.ts';
import type { TotalRoundsRequestDto } from '@/tournament-api/dtos/tournament.dto.ts';
import { standingsEntryDtoToDomain } from '@/tournament-api/mappers/standings.mapper.ts';
import type { StandingsEntry } from '@/domain/standings.domain.ts';
import type { Tournament, TournamentWriteInput } from '@/domain/tournament.domain.ts';
import type { Round } from '@/domain/round.domain.ts';
import { apiClient } from '@/shared/api/api-client.ts';

/**
 * The only layer that knows the tournament endpoints and wire DTOs. Each function parses
 * the response with zod and maps it to a domain `Tournament`; ownership is enforced
 * server-side from the session cookie.
 */
async function list(): Promise<Tournament[]> {
    const { data } = await apiClient.get<unknown>('/api/tournaments');
    return tournamentListDtoSchema.parse(data).map(tournamentDtoToDomain);
}

async function get(id: string): Promise<Tournament> {
    const { data } = await apiClient.get<unknown>(`/api/tournaments/${id}`);
    return tournamentDtoToDomain(tournamentDtoSchema.parse(data));
}

async function create(input: TournamentWriteInput): Promise<Tournament> {
    const { data } = await apiClient.post<unknown>(
        '/api/tournaments',
        tournamentWriteInputToDto(input),
    );
    return tournamentDtoToDomain(tournamentDtoSchema.parse(data));
}

async function update(id: string, input: TournamentWriteInput): Promise<Tournament> {
    const { data } = await apiClient.put<unknown>(
        `/api/tournaments/${id}`,
        tournamentWriteInputToDto(input),
    );
    return tournamentDtoToDomain(tournamentDtoSchema.parse(data));
}

async function start(id: string): Promise<{ tournament: Tournament; round: Round }> {
    const { data } = await apiClient.post<unknown>(`/api/tournaments/${id}/start`);
    const { tournament, round } = startedTournamentDtoSchema.parse(data);
    return {
        tournament: tournamentDtoToDomain(tournament),
        round: roundDtoToDomain(round),
    };
}

async function rounds(id: string): Promise<Round[]> {
    const { data } = await apiClient.get<unknown>(`/api/tournaments/${id}/rounds`);
    return roundListDtoSchema.parse(data).map(roundDtoToDomain);
}

/** Starts the next round, drawn by the server from the current standings. */
async function startNextRound(id: string): Promise<Round> {
    const { data } = await apiClient.post<unknown>(`/api/tournaments/${id}/rounds`);
    return roundDtoToDomain(roundDtoSchema.parse(data));
}

/** The admin-only standings, in the server's order (ranks and ties computed there). */
async function standings(id: string): Promise<StandingsEntry[]> {
    const { data } = await apiClient.get<unknown>(`/api/tournaments/${id}/standings`);
    return standingsDtoSchema.parse(data).map(standingsEntryDtoToDomain);
}

/** Changes only the Total Rounds; the one setting still editable while ACTIVE. */
async function setTotalRounds(id: string, totalRounds: number): Promise<Tournament> {
    const body: TotalRoundsRequestDto = { totalRounds };
    const { data } = await apiClient.put<unknown>(`/api/tournaments/${id}/total-rounds`, body);
    return tournamentDtoToDomain(tournamentDtoSchema.parse(data));
}

/** Finishes the tournament once every round is played; fixes the Final Ranking (organizer-only until published). */
async function finish(id: string): Promise<Tournament> {
    const { data } = await apiClient.post<unknown>(`/api/tournaments/${id}/finish`);
    return tournamentDtoToDomain(tournamentDtoSchema.parse(data));
}

/** Publishes the Final Ranking so the teams can see it. */
async function publish(id: string): Promise<Tournament> {
    const { data } = await apiClient.post<unknown>(`/api/tournaments/${id}/publish`);
    return tournamentDtoToDomain(tournamentDtoSchema.parse(data));
}

async function remove(id: string): Promise<void> {
    await apiClient.delete(`/api/tournaments/${id}`);
}

export const TournamentRepository = {
    list,
    get,
    create,
    update,
    remove,
    start,
    rounds,
    standings,
    setTotalRounds,
    startNextRound,
    finish,
    publish,
};
