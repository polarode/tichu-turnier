import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { teamQueryKey, teamsQueryKey } from '@/shared/constants/query-keys.ts';
import type { TeamWriteRequestDto } from '@/team-api/dtos/team.dto.ts';
import { TeamRepository } from '@/team-api/team.repository.ts';

/**
 * TanStack Query hooks for a tournament's teams. Mutations invalidate the tournament's
 * team prefix, which also covers every single-team key, so the setup screen stays in sync.
 */
export function useTeams(tournamentId: string) {
    return useQuery({
        queryKey: teamsQueryKey(tournamentId),
        queryFn: () => TeamRepository.list(tournamentId),
    });
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
        mutationFn: (request: TeamWriteRequestDto) =>
            TeamRepository.create(tournamentId, request),
        onSuccess: () => queryClient.invalidateQueries({ queryKey: teamsQueryKey(tournamentId) }),
    });
}

export function useUpdateTeam(tournamentId: string) {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: ({ teamId, request }: { teamId: string; request: TeamWriteRequestDto }) =>
            TeamRepository.update(tournamentId, teamId, request),
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
