import { teamDtoToDomain, teamWriteInputToDto } from '@/team-api/mappers/team.mapper.ts';
import { teamDtoSchema, teamListDtoSchema } from '@/team-api/dtos/team.dto.ts';
import type { Team, TeamWriteInput } from '@/domain/team.domain.ts';
import { apiClient } from '@/shared/api/api-client.ts';

const teamsPath = (tournamentId: string) => `/api/tournaments/${tournamentId}/teams`;

/**
 * The only layer that knows the team endpoints and wire DTOs. Each function parses the
 * response with zod and maps it to a domain `Team`; ownership and validation stay
 * server-side.
 */
async function list(tournamentId: string): Promise<Team[]> {
    const { data } = await apiClient.get<unknown>(teamsPath(tournamentId));
    return teamListDtoSchema.parse(data).map(teamDtoToDomain);
}

async function get(tournamentId: string, teamId: string): Promise<Team> {
    const { data } = await apiClient.get<unknown>(`${teamsPath(tournamentId)}/${teamId}`);
    return teamDtoToDomain(teamDtoSchema.parse(data));
}

async function create(tournamentId: string, input: TeamWriteInput): Promise<Team> {
    const { data } = await apiClient.post<unknown>(
        teamsPath(tournamentId),
        teamWriteInputToDto(input),
    );
    return teamDtoToDomain(teamDtoSchema.parse(data));
}

async function update(
    tournamentId: string,
    teamId: string,
    input: TeamWriteInput,
): Promise<Team> {
    const { data } = await apiClient.put<unknown>(
        `${teamsPath(tournamentId)}/${teamId}`,
        teamWriteInputToDto(input),
    );
    return teamDtoToDomain(teamDtoSchema.parse(data));
}

async function remove(tournamentId: string, teamId: string): Promise<void> {
    await apiClient.delete(`${teamsPath(tournamentId)}/${teamId}`);
}

export const TeamRepository = {
    list,
    get,
    create,
    update,
    remove,
};
