import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import type { Tournament, TournamentWriteInput } from '@/domain/tournament.domain.ts';
import { QueryKeys, standingsQueryKey, tournamentQueryKey } from '@/shared/constants/query-keys.ts';
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
        refetchOnWindowFocus: true,
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

/**
 * Changes the Total Rounds, one step per call. Optimistic: the detail shows the new
 * value at once and rolls back when the server rejects it (e.g. below the rounds
 * already drawn); the caller shows the error.
 */
export function useSetTotalRounds(id: string) {
    const queryClient = useQueryClient();
    const key = tournamentQueryKey(id);
    return useMutation({
        mutationFn: (totalRounds: number) => TournamentRepository.setTotalRounds(id, totalRounds),
        onMutate: async (totalRounds) => {
            await queryClient.cancelQueries({ queryKey: key });
            const previous = queryClient.getQueryData<Tournament>(key);
            if (previous) {
                queryClient.setQueryData<Tournament>(key, {
                    ...previous,
                    settings: { ...previous.settings, totalRounds },
                });
            }
            return { previous };
        },
        onError: (_error, _totalRounds, context) => {
            if (context?.previous) queryClient.setQueryData(key, context.previous);
        },
        onSuccess: (tournament) => queryClient.setQueryData(key, tournament),
    });
}

/**
 * Finishes the tournament after its last round. The returned tournament replaces the
 * cached one at once; the list (which covers the detail) and the standings refetch.
 */
export function useFinishTournament(id: string) {
    return useTournamentLifecycleMutation(id, () => TournamentRepository.finish(id));
}

/** Publishes the Final Ranking to the teams; same cache handling as {@link useFinishTournament}. */
export function usePublishTournament(id: string) {
    return useTournamentLifecycleMutation(id, () => TournamentRepository.publish(id));
}

function useTournamentLifecycleMutation(id: string, mutationFn: () => Promise<Tournament>) {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn,
        onSuccess: (updated) => {
            queryClient.setQueryData(tournamentQueryKey(id), updated);
            void queryClient.invalidateQueries({ queryKey: [QueryKeys.TOURNAMENTS] });
            void queryClient.invalidateQueries({ queryKey: standingsQueryKey(id) });
        },
    });
}
