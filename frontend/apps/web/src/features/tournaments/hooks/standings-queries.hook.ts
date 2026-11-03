import { useQuery } from '@tanstack/react-query';

import type { StandingsEntry } from '@/domain/standings.domain.ts';
import { standingsQueryKey } from '@/shared/constants/query-keys.ts';
import { TournamentRepository } from '@/tournament-api/tournament.repository.ts';

/** Poll interval while tables complete; S8's live updates replace the polling, as for the rounds. */
const LIVE_STANDINGS_REFETCH_MS = 30_000;

/**
 * The admin-only Standings, ordered and ranked by the server. With [live] set (the
 * tournament is ACTIVE) the query polls so newly completed tables show up.
 */
export function useStandings(tournamentId: string, { live = false }: { live?: boolean } = {}) {
    return useQuery({
        queryKey: standingsQueryKey(tournamentId),
        queryFn: (): Promise<StandingsEntry[]> => TournamentRepository.standings(tournamentId),
        refetchInterval: live ? LIVE_STANDINGS_REFETCH_MS : false,
    });
}
