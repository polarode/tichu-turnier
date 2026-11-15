import {
    teamDtoToDomain,
    teamWriteInputToDto,
    withdrawalDtoToDomain,
    withdrawalInputToDto,
} from '@/team-api/mappers/team.mapper.ts';
import { teamDtoSchema, teamListDtoSchema, withdrawalDtoSchema } from '@/team-api/dtos/team.dto.ts';
import { accessLinkRevealDtoToDomain } from '@/team-api/mappers/access-link.mapper.ts';
import { accessLinkRevealDtoSchema } from '@/team-api/dtos/access-link.dto.ts';
import type {
    Team,
    TeamWithdrawal,
    TeamWriteInput,
    WithdrawalInput,
} from '@/domain/team.domain.ts';
import type { RevealedAccessLink } from '@/domain/access-link.domain.ts';
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

async function update(tournamentId: string, teamId: string, input: TeamWriteInput): Promise<Team> {
    const { data } = await apiClient.put<unknown>(
        `${teamsPath(tournamentId)}/${teamId}`,
        teamWriteInputToDto(input),
    );
    return teamDtoToDomain(teamDtoSchema.parse(data));
}

async function remove(tournamentId: string, teamId: string): Promise<void> {
    await apiClient.delete(`${teamsPath(tournamentId)}/${teamId}`);
}

type AccessLinkVerb = 'reveal' | 'rotate' | 'revoke';

const accessLinkPath = (tournamentId: string, teamId: string, verb: AccessLinkVerb) =>
    `${teamsPath(tournamentId)}/${teamId}/access-link/${verb}`;

/** The one call whose response carries raw link material — reveal fails with
 *  `LINK_ALREADY_REVEALED` once shown; rotation is the documented recovery. */
async function revealAccessLink(tournamentId: string, teamId: string): Promise<RevealedAccessLink> {
    const { data } = await apiClient.post<unknown>(accessLinkPath(tournamentId, teamId, 'reveal'));
    return accessLinkRevealDtoToDomain(accessLinkRevealDtoSchema.parse(data));
}

async function rotateAccessLink(tournamentId: string, teamId: string): Promise<void> {
    await apiClient.post(accessLinkPath(tournamentId, teamId, 'rotate'));
}

async function revokeAccessLink(tournamentId: string, teamId: string): Promise<void> {
    await apiClient.post(accessLinkPath(tournamentId, teamId, 'revoke'));
}

const withdrawalPath = (tournamentId: string, teamId: string) =>
    `${teamsPath(tournamentId)}/${teamId}/withdrawal`;

/** Withdraws the team (ADR 0011): its played games stand, its owed games become Forfeit Games. */
async function withdraw(
    tournamentId: string,
    teamId: string,
    input: WithdrawalInput,
): Promise<TeamWithdrawal> {
    const { data } = await apiClient.post<unknown>(
        withdrawalPath(tournamentId, teamId),
        withdrawalInputToDto(input),
    );
    return withdrawalDtoToDomain(withdrawalDtoSchema.parse(data));
}

/** Undoes the team's Withdrawal, removing its Forfeit Games (ADR 0011). */
async function undoWithdrawal(tournamentId: string, teamId: string): Promise<void> {
    await apiClient.delete(withdrawalPath(tournamentId, teamId));
}

export const TeamRepository = {
    list,
    get,
    create,
    update,
    remove,
    revealAccessLink,
    rotateAccessLink,
    revokeAccessLink,
    withdraw,
    undoWithdrawal,
};
