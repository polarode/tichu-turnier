import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import {
    type GameWriteInput,
    isCompleted,
    type RecordedGame,
    type TableConfirmation,
    type TableRef,
    type TeamTable,
} from '@/domain/game.domain.ts';
import { ScoringRepository } from '@/scoring-api/scoring.repository.ts';
import {
    teamCurrentTableQueryKey,
    teamGamesQueryKey,
} from '@/shared/constants/query-keys.ts';

/** Poll interval while the table is open; S8's live updates (#36) replace the polling. */
const OPEN_TABLE_REFETCH_MS = 30_000;

/** Polls until the table is completed: after that nothing at it can change. */
const pollUntilCompleted = (table: TeamTable | undefined) =>
    table && isCompleted(table.confirmation) ? false : OPEN_TABLE_REFETCH_MS;

/**
 * Query options for the team's current table (`GET /api/team/me/current-table`).
 * Polls while the table is not completed, so a confirmation by the other team
 * shows up. A missing team session (401, or 403 with an admin cookie in the
 * browser) is handled by the route guard, so no retry.
 */
export const teamCurrentTableQueryOptions = {
    queryKey: teamCurrentTableQueryKey(),
    queryFn: (): Promise<TeamTable> => ScoringRepository.currentTable(),
    retry: false,
    refetchInterval: (query: { state: { data: TeamTable | undefined } }) => pollUntilCompleted(query.state.data),
};

export function useCurrentTable() {
    return useQuery(teamCurrentTableQueryOptions);
}

/** Reloads the current table, e.g. after a write found it frozen, so its state can be explained. */
export function useReloadCurrentTable() {
    const queryClient = useQueryClient();
    return () => queryClient.invalidateQueries({ queryKey: teamCurrentTableQueryKey() });
}

const gamesQueryKeyOf = (table: TableRef) =>
    teamGamesQueryKey(table.tournamentId, table.roundNumber, table.tableNumber);

export const teamGamesQueryOptions = (table: TableRef) => ({
    queryKey: gamesQueryKeyOf(table),
    queryFn: (): Promise<RecordedGame[]> => ScoringRepository.games(table),
});

/** The games at [table]; polls alongside the current table until it is completed. */
export function useTableGames(table: TeamTable) {
    return useQuery({ ...teamGamesQueryOptions(table), refetchInterval: pollUntilCompleted(table) });
}

/** After a confirm or retract, both the table state and its games reload from the server. */
function useInvalidateTable(table: TableRef) {
    const queryClient = useQueryClient();
    return () =>
        Promise.all([
            queryClient.invalidateQueries({ queryKey: teamCurrentTableQueryKey() }),
            queryClient.invalidateQueries({ queryKey: gamesQueryKeyOf(table) }),
        ]);
}

/**
 * Confirms [table] for the session's team with the games as the team saw them.
 * Settles either way by reloading the table and games: on success the status
 * moved, and on a rejection (stale games, table changed) the view must catch up.
 */
export function useConfirmTable(table: TableRef) {
    const invalidate = useInvalidateTable(table);
    return useMutation({
        mutationFn: (games: readonly RecordedGame[]): Promise<TableConfirmation> =>
            ScoringRepository.confirm(table, games),
        onSettled: invalidate,
    });
}

/** Retracts the session team's confirmation of [table]; reloads like [useConfirmTable]. */
export function useRetractTable(table: TableRef) {
    const invalidate = useInvalidateTable(table);
    return useMutation({
        mutationFn: (): Promise<TableConfirmation> => ScoringRepository.retract(table),
        onSettled: invalidate,
    });
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
            void queryClient.invalidateQueries({ queryKey: gamesQueryKeyOf(table) });
        },
    });
}
