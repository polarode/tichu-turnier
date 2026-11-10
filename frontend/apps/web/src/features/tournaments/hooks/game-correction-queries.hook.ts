import { useMutation, useQueryClient } from '@tanstack/react-query';

import type { GameWriteInput, RecordedGame, TableRef } from '@/domain/game.domain.ts';
import {
    roundsQueryKey,
    standingsQueryKey,
    statisticsQueryKey,
} from '@/shared/constants/query-keys.ts';
import { TournamentRepository } from '@/tournament-api/tournament.repository.ts';

/**
 * Corrects a game at a completed table (ADR 0010). On success the rounds refetch and
 * the standings and statistics are invalidated: both are computed on read, so they
 * pick the correction up. The pairings of rounds already drawn stay as they are.
 */
export function useCorrectGame(tournamentId: string) {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: ({
            table,
            input,
        }: {
            table: TableRef;
            input: GameWriteInput;
        }): Promise<RecordedGame> => TournamentRepository.correctGame(table, input),
        onSuccess: () => {
            void queryClient.invalidateQueries({ queryKey: roundsQueryKey(tournamentId) });
            void queryClient.invalidateQueries({ queryKey: standingsQueryKey(tournamentId) });
            void queryClient.invalidateQueries({ queryKey: statisticsQueryKey(tournamentId) });
        },
    });
}
