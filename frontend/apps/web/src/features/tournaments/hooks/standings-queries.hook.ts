import { useQuery } from '@tanstack/react-query';

import type { StandingsEntry } from '@/domain/standings.domain.ts';
import { standingsQueryKey } from '@/shared/constants/query-keys.ts';
import { TournamentRepository } from '@/tournament-api/tournament.repository.ts';

/**
 * The admin-only Standings, ordered and ranked by the server. Kept fresh by the live
 * stream and by refetch on window focus, not by polling.
 */
export function useStandings(tournamentId: string) {
    return useQuery({
        queryKey: standingsQueryKey(tournamentId),
        queryFn: (): Promise<StandingsEntry[]> => TournamentRepository.standings(tournamentId),
        refetchOnWindowFocus: true,
    });
}
