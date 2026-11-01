import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import type { Round } from '@/domain/round.domain.ts';
import { roundsQueryKey, tournamentQueryKey } from '@/shared/constants/query-keys.ts';
import { TournamentRepository } from '@/tournament-api/tournament.repository.ts';

/**
 * TanStack Query hooks for the start flow. The start mutation invalidates both the
 * tournament (status changed) and the rounds (round 1 created); the rounds query is
 * what the rounds section renders and what SSE-driven invalidation will target later.
 */
export function useStartTournament() {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: (id: string) => TournamentRepository.start(id),
        onSuccess: (started, id) => {
            queryClient.setQueryData(tournamentQueryKey(id), started.tournament);
            queryClient.setQueryData(roundsQueryKey(id), [started.round]);
        },
    });
}

/** Poll interval while results come in; S8's live updates replace the polling. */
const LIVE_ROUNDS_REFETCH_MS = 30_000;

/**
 * The tournament's rounds with each table's entered games. With [live] set (the
 * tournament is ACTIVE) the query polls so the organizer sees new results;
 * with [enabled] false it does not fetch (a tournament still in setup has no rounds).
 */
export function useRounds(
    tournamentId: string,
    { live = false, enabled = true }: { live?: boolean; enabled?: boolean } = {},
) {
    return useQuery({
        queryKey: roundsQueryKey(tournamentId),
        queryFn: (): Promise<Round[]> => TournamentRepository.rounds(tournamentId),
        refetchInterval: live ? LIVE_ROUNDS_REFETCH_MS : false,
        enabled,
    });
}
