import { publicTournamentQueryKey } from '@/shared/constants/query-keys.ts';
import { useLiveStream } from '@/shared/hooks/use-live-stream.hook.ts';
import { type SseSubscription } from '@/shared/sse/sse-client.ts';
import { TournamentEventType } from '@/vocabulary/tournament-event-type.enum.ts';
import { TournamentStatus } from '@/vocabulary/tournament-status.enum.ts';

/**
 * The events the anonymous public stream carries. Each one changes the public pairings:
 * a new round, a finish (the status) or the publish (the Final Ranking and table totals).
 */
function subscriptionsFor(tournamentId: string): SseSubscription[] {
    const detail = publicTournamentQueryKey(tournamentId);
    return [
        { event: TournamentEventType.ROUND_STARTED, queryKeys: [detail] },
        { event: TournamentEventType.TOURNAMENT_FINISHED, queryKeys: [detail] },
        { event: TournamentEventType.RESULTS_PUBLISHED, queryKeys: [detail] },
    ];
}

/**
 * Follows the public live stream of one tournament's page. The stream follows the
 * tournament until its results are published; after that the page no longer changes,
 * so the connection is closed. `live` says whether the stream applies at all (the status
 * is known and not published), `connected` whether it is up (only meaningful while `live`).
 *
 * Every (re)connect refetches the page, so events missed while offline are caught up.
 */
export function usePublicTournamentLiveStream(
    tournamentId: string,
    status: TournamentStatus | undefined,
): { live: boolean; connected: boolean } {
    const live = status !== undefined && status !== TournamentStatus.PUBLISHED;
    const connected = useLiveStream({
        url: `/api/public/tournaments/${tournamentId}/events`,
        enabled: live,
        subscriptions: subscriptionsFor(tournamentId),
        keysOnConnect: [publicTournamentQueryKey(tournamentId)],
    });
    return { live, connected };
}
