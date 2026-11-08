import { useQuery } from '@tanstack/react-query';

import type { TournamentStatistics } from '@/domain/statistics.domain.ts';
import { statisticsQueryKey } from '@/shared/constants/query-keys.ts';
import { StatisticsRepository } from '@/statistics-api/statistics.repository.ts';

/**
 * The admin-only statistics of one tournament. Kept fresh by refetch on window focus, not
 * by polling; a finished or published tournament changes little, so no live stream here.
 */
export function useStatistics(tournamentId: string) {
    return useQuery({
        queryKey: statisticsQueryKey(tournamentId),
        queryFn: (): Promise<TournamentStatistics> => StatisticsRepository.organizer(tournamentId),
        refetchOnWindowFocus: true,
    });
}
