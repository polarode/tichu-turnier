import { useEffect, useState } from 'react';
import { type QueryKey, useQueryClient } from '@tanstack/react-query';

import {
    roundsQueryKey,
    standingsQueryKey,
    teamsQueryKey,
    tournamentQueryKey,
} from '@/shared/constants/query-keys.ts';
import { SseClient, type SseSubscription } from '@/shared/sse/sse-client.ts';
import { TournamentEventType } from '@/vocabulary/tournament-event-type.enum.ts';

/** The query keys each event on the organizer's stream makes stale. */
function subscriptionsFor(tournamentId: string): SseSubscription[] {
    const tournament = tournamentQueryKey(tournamentId);
    const rounds = roundsQueryKey(tournamentId);
    const standings = standingsQueryKey(tournamentId);
    const queryKeysByEvent: Record<TournamentEventType, readonly QueryKey[]> = {
        // A recorded game or a confirmation change alters the tables' results.
        [TournamentEventType.GAME_RECORDED]: [rounds, standings],
        [TournamentEventType.TABLE_CONFIRMED]: [rounds, standings],
        [TournamentEventType.CONFIRMATION_RETRACTED]: [rounds, standings],
        // A start or finish changes the status and the round list.
        [TournamentEventType.TOURNAMENT_STARTED]: [tournament, rounds, standings],
        [TournamentEventType.ROUND_STARTED]: [tournament, rounds, standings],
        [TournamentEventType.TOURNAMENT_FINISHED]: [tournament, rounds, standings],
        // Only the tournament's own fields change.
        [TournamentEventType.TOTAL_ROUNDS_CHANGED]: [tournament],
        [TournamentEventType.RESULTS_PUBLISHED]: [tournament],
    };
    return Object.entries(queryKeysByEvent).map(([event, queryKeys]) => ({ event, queryKeys }));
}

/**
 * Follows the tournament's live event stream while [enabled]: each event refetches the
 * queries it makes stale (see {@link subscriptionsFor}), and every (re)connect refetches
 * everything shown on the page, so events missed while offline are caught up. Returns
 * whether the stream is connected.
 */
export function useTournamentLiveStream(tournamentId: string, enabled: boolean): boolean {
    const queryClient = useQueryClient();
    const [connected, setConnected] = useState(false);

    useEffect(() => {
        if (!enabled) return;
        const client = new SseClient({
            url: `/api/tournaments/${tournamentId}/events`,
            queryClient,
            subscriptions: subscriptionsFor(tournamentId),
            onStateChange: (isConnected) => {
                setConnected(isConnected);
                if (!isConnected) return;
                for (const queryKey of [
                    tournamentQueryKey(tournamentId),
                    teamsQueryKey(tournamentId),
                    roundsQueryKey(tournamentId),
                    standingsQueryKey(tournamentId),
                ]) {
                    void queryClient.invalidateQueries({ queryKey });
                }
            },
        });
        client.connect();
        return () => {
            client.disconnect();
            setConnected(false);
        };
    }, [tournamentId, enabled, queryClient]);

    return connected;
}
