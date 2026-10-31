import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import type { GameWriteInput, RecordedGame, TableRef, TeamTable } from '@/domain/game.domain.ts';
import { ScoringRepository } from '@/scoring-api/scoring.repository.ts';
import {
    teamCurrentTableQueryKey,
    teamGamesQueryKey,
} from '@/shared/constants/query-keys.ts';

/**
 * Query options for the team's current table (`GET /api/team/me/current-table`).
 * A 401 means the session is gone; the route guard handles that, so no retry.
 */
export const teamCurrentTableQueryOptions = {
    queryKey: teamCurrentTableQueryKey(),
    queryFn: (): Promise<TeamTable> => ScoringRepository.currentTable(),
    retry: false,
};

export function useCurrentTable() {
    return useQuery(teamCurrentTableQueryOptions);
}

export const teamGamesQueryOptions = (table: TableRef) => ({
    queryKey: teamGamesQueryKey(table.tournamentId, table.roundNumber, table.tableNumber),
    queryFn: (): Promise<RecordedGame[]> => ScoringRepository.games(table),
});

export function useTableGames(table: TableRef) {
    return useQuery(teamGamesQueryOptions(table));
}

/**
 * Records a game: POST when [GameWriteInput.version] is null (create), PUT
 * otherwise (explicit edit). On success the table's games are invalidated so the
 * selector and any open form reload the server-authoritative state.
 */
export function useRecordGame(table: TableRef) {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: (input: GameWriteInput): Promise<RecordedGame> =>
            input.version === null
                ? ScoringRepository.create(table, input)
                : ScoringRepository.update(table, input),
        onSuccess: () => {
            void queryClient.invalidateQueries({
                queryKey: teamGamesQueryKey(table.tournamentId, table.roundNumber, table.tableNumber),
            });
        },
    });
}
