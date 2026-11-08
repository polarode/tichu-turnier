import type { TournamentStatistics } from '@/domain/statistics.domain.ts';
import { statisticsDtoSchema } from '@/statistics-api/dtos/statistics.dto.ts';
import { statisticsDtoToDomain } from '@/statistics-api/mappers/statistics.mapper.ts';
import { apiClient } from '@/shared/api/api-client.ts';

/**
 * The only layer that knows the statistics endpoints and wire DTOs. The organizer and the
 * public read answer the same body; each parses it with zod and maps it to the domain `TournamentStatistics`.
 */

/** The organizer's statistics of one tournament (admin-only; 409 while still in setup). */
async function organizer(tournamentId: string): Promise<TournamentStatistics> {
    const { data } = await apiClient.get<unknown>(`/api/tournaments/${tournamentId}/statistics`);
    return statisticsDtoToDomain(statisticsDtoSchema.parse(data));
}

/** The anonymous statistics of one public tournament (404 when not visible; 409 until published). */
async function publicStatistics(tournamentId: string): Promise<TournamentStatistics> {
    const { data } = await apiClient.get<unknown>(
        `/api/public/tournaments/${tournamentId}/statistics`,
    );
    return statisticsDtoToDomain(statisticsDtoSchema.parse(data));
}

export const StatisticsRepository = {
    organizer,
    publicStatistics,
};
