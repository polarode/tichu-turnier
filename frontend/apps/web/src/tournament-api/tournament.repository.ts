import type { Tournament } from '@/domain/tournament.domain.ts';
import {
    tournamentDtoSchema,
    tournamentListDtoSchema,
    type TournamentWriteRequestDto,
} from '@/tournament-api/dtos/tournament.dto.ts';
import { tournamentDtoToDomain } from '@/tournament-api/mappers/tournament.mapper.ts';
import { apiClient } from '@/shared/api/api-client.ts';

/**
 * The only layer that knows the tournament endpoints. Each function parses the
 * response with zod and maps it to a domain `Tournament`; ownership is enforced
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

async function create(request: TournamentWriteRequestDto): Promise<Tournament> {
    const { data } = await apiClient.post<unknown>('/api/tournaments', request);
    return tournamentDtoToDomain(tournamentDtoSchema.parse(data));
}

async function update(id: string, request: TournamentWriteRequestDto): Promise<Tournament> {
    const { data } = await apiClient.put<unknown>(`/api/tournaments/${id}`, request);
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
};
