import { useQuery } from '@tanstack/react-query';

import { publicTournamentsQueryKey } from '@/shared/constants/query-keys.ts';
import { TournamentRepository } from '@/tournament-api/tournament.repository.ts';

/**
 * The anonymous list of public tournaments for the home page, already ordered by the
 * backend (active, completed, published). No session is needed.
 */
export function usePublicTournaments() {
    return useQuery({
        queryKey: publicTournamentsQueryKey(),
        queryFn: TournamentRepository.listPublicTournaments,
    });
}
