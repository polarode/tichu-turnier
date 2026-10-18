import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import type { TournamentWriteInput } from '@/domain/tournament.domain.ts';
import { QueryKeys, tournamentQueryKey } from '@/shared/constants/query-keys.ts';
import { TournamentRepository } from '@/tournament-api/tournament.repository.ts';

/**
 * TanStack Query hooks for tournaments. Components call these; they never touch the
 * repository directly. Mutations invalidate the list (which also covers every detail
 * key, since it is a prefix) so the UI stays in sync.
 */
export function useTournaments() {
    return useQuery({
        queryKey: [QueryKeys.TOURNAMENTS],
        queryFn: TournamentRepository.list,
    });
}

export function useTournament(id: string) {
    return useQuery({
        queryKey: tournamentQueryKey(id),
        queryFn: () => TournamentRepository.get(id),
    });
}

export function useCreateTournament() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: (input: TournamentWriteInput) => TournamentRepository.create(input),
        onSuccess: () => queryClient.invalidateQueries({ queryKey: [QueryKeys.TOURNAMENTS] }),
    });
}

export function useUpdateTournament() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: ({ id, input }: { id: string; input: TournamentWriteInput }) =>
            TournamentRepository.update(id, input),
        onSuccess: () => queryClient.invalidateQueries({ queryKey: [QueryKeys.TOURNAMENTS] }),
    });
}

export function useDeleteTournament() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: (id: string) => TournamentRepository.remove(id),
        onSuccess: () => queryClient.invalidateQueries({ queryKey: [QueryKeys.TOURNAMENTS] }),
    });
}
