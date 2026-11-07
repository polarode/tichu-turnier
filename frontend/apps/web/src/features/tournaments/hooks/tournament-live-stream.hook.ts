import { useEffect, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';

import {
    roundsQueryKey,
    standingsQueryKey,
    teamsQueryKey,
    tournamentQueryKey,
} from '@/shared/constants/query-keys.ts';
import { SseClient } from '@/shared/sse/sse-client.ts';
import { TournamentEventType } from '@/vocabulary/tournament-event-type.enum.ts';

/**
 * Follows the tournament's live event stream while [enabled]: a recorded game refetches
 * rounds and standings, and every (re)connect refetches everything shown on the page, so
 * events missed while offline are caught up. Returns whether the stream is connected.
 */
export function useTournamentLiveStream(tournamentId: string, enabled: boolean): boolean {
    const queryClient = useQueryClient();
    const [connected, setConnected] = useState(false);

    useEffect(() => {
        if (!enabled) return;
        const client = new SseClient({
            url: `/api/tournaments/${tournamentId}/events`,
            queryClient,
            subscriptions: [
                {
                    event: TournamentEventType.GAME_RECORDED,
                    queryKeys: [roundsQueryKey(tournamentId), standingsQueryKey(tournamentId)],
                },
            ],
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
