import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import type { TeamWriteInput } from '@/domain/team.domain.ts';
import { teamQueryKey, teamsQueryKey } from '@/shared/constants/query-keys.ts';
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
