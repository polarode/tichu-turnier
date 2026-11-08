import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import {
    type GameWriteInput,
    type RecordedGame,
    type TableConfirmation,
    type TableRef,
    type TeamTable,
} from '@/domain/game.domain.ts';
import { ScoringRepository } from '@/scoring-api/scoring.repository.ts';
import { teamCurrentTableQueryKey, teamGamesQueryKey } from '@/shared/constants/query-keys.ts';

/**
 * Query options for the team's current table (`GET /api/team/me/current-table`).
 * Changes to it arrive on the team's live stream; a focus refetch covers the case
 * where the stream was down while the window was away. A missing team session
 * (401, or 403 with an admin cookie in the browser) is handled by the route guard,
 * so no retry.
 */
export const teamCurrentTableQueryOptions = {
    queryKey: teamCurrentTableQueryKey(),
    queryFn: (): Promise<TeamTable> => ScoringRepository.currentTable(),
    retry: false,
    refetchOnWindowFocus: true,
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

/**
 * The games at [table]. Changes arrive on the team's live stream; a focus refetch
 * covers the case where the stream was down while the window was away.
 */
export function useTableGames(table: TeamTable) {
    return useQuery({ ...teamGamesQueryOptions(table), refetchOnWindowFocus: true });
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
