import { useQuery } from '@tanstack/react-query';

import type { TournamentStatistics } from '@/domain/statistics.domain.ts';
import {
    publicStatisticsQueryKey,
    publicTournamentQueryKey,
    publicTournamentsQueryKey,
} from '@/shared/constants/query-keys.ts';
import { StatisticsRepository } from '@/statistics-api/statistics.repository.ts';
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

/**
 * The anonymous pairings of one public tournament. A tournament that is not public (or
 * does not exist) fails with the backend's NOT_FOUND; no session is needed.
 */
export function usePublicTournament(id: string) {
    return useQuery({
        queryKey: publicTournamentQueryKey(id),
        queryFn: () => TournamentRepository.getPublicTournament(id),
    });
}

/**
 * The anonymous statistics of one published public tournament. Only asked for while the
 * Statistics tab is open (`enabled`), so the page does not fetch them on the Results tab.
 */
export function usePublicStatistics(id: string, enabled: boolean) {
    return useQuery({
        queryKey: publicStatisticsQueryKey(id),
        queryFn: (): Promise<TournamentStatistics> => StatisticsRepository.publicStatistics(id),
        enabled,
    });
}
