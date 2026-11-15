import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query';

import type { TeamWriteInput, WithdrawalInput } from '@/domain/team.domain.ts';
import {
    publicStatisticsQueryKey,
    publicTournamentQueryKey,
    roundsQueryKey,
    standingsQueryKey,
    statisticsQueryKey,
    teamQueryKey,
    teamsQueryKey,
} from '@/shared/constants/query-keys.ts';
import { TeamRepository } from '@/team-api/team.repository.ts';

/**
 * Query options for a tournament's teams, shared by `useTeams()` and the dashboard's
 * parallel team-count queries.
 */
export const teamsQueryOptions = (tournamentId: string) => ({
    queryKey: teamsQueryKey(tournamentId),
    queryFn: () => TeamRepository.list(tournamentId),
});

/**
 * TanStack Query hooks for a tournament's teams. Mutations invalidate the tournament's
 * team prefix, which also covers every single-team key, so the setup screen stays in sync.
 */
export function useTeams(tournamentId: string) {
    return useQuery(teamsQueryOptions(tournamentId));
}

export function useTeam(tournamentId: string, teamId: string) {
    return useQuery({
        queryKey: teamQueryKey(tournamentId, teamId),
        queryFn: () => TeamRepository.get(tournamentId, teamId),
    });
}

export function useCreateTeam(tournamentId: string) {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: (input: TeamWriteInput) => TeamRepository.create(tournamentId, input),
        onSuccess: () => queryClient.invalidateQueries({ queryKey: teamsQueryKey(tournamentId) }),
    });
}

export function useUpdateTeam(tournamentId: string) {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: ({ teamId, input }: { teamId: string; input: TeamWriteInput }) =>
            TeamRepository.update(tournamentId, teamId, input),
        onSuccess: () => queryClient.invalidateQueries({ queryKey: teamsQueryKey(tournamentId) }),
    });
}

export function useDeleteTeam(tournamentId: string) {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: (teamId: string) => TeamRepository.remove(tournamentId, teamId),
        onSuccess: () => queryClient.invalidateQueries({ queryKey: teamsQueryKey(tournamentId) }),
    });
}

export function useRevealAccessLink(tournamentId: string) {
    return useMutation({
        mutationFn: (teamId: string) => TeamRepository.revealAccessLink(tournamentId, teamId),
    });
}

export function useRotateAccessLink(tournamentId: string) {
    return useMutation({
        mutationFn: (teamId: string) => TeamRepository.rotateAccessLink(tournamentId, teamId),
    });
}

export function useRevokeAccessLink(tournamentId: string) {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: (teamId: string) => TeamRepository.revokeAccessLink(tournamentId, teamId),
        onSuccess: () => queryClient.invalidateQueries({ queryKey: teamsQueryKey(tournamentId) }),
    });
}

/**
 * A withdrawal changes what the tournament's pairings, standings and statistics show, and
 * the public view of a published tournament, so each of them is refreshed.
 */
function invalidateAfterWithdrawal(queryClient: QueryClient, tournamentId: string) {
    return Promise.all([
        queryClient.invalidateQueries({ queryKey: teamsQueryKey(tournamentId) }),
        queryClient.invalidateQueries({ queryKey: roundsQueryKey(tournamentId) }),
        queryClient.invalidateQueries({ queryKey: standingsQueryKey(tournamentId) }),
        queryClient.invalidateQueries({ queryKey: statisticsQueryKey(tournamentId) }),
        queryClient.invalidateQueries({ queryKey: publicTournamentQueryKey(tournamentId) }),
        queryClient.invalidateQueries({ queryKey: publicStatisticsQueryKey(tournamentId) }),
    ]);
}

export function useWithdrawTeam(tournamentId: string) {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: ({ teamId, input }: { teamId: string; input: WithdrawalInput }) =>
            TeamRepository.withdraw(tournamentId, teamId, input),
        onSuccess: () => invalidateAfterWithdrawal(queryClient, tournamentId),
    });
}

export function useUndoWithdrawal(tournamentId: string) {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: (teamId: string) => TeamRepository.undoWithdrawal(tournamentId, teamId),
        onSuccess: () => invalidateAfterWithdrawal(queryClient, tournamentId),
    });
}
