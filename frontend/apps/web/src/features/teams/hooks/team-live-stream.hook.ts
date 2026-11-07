import { type QueryKey } from '@tanstack/react-query';

import type { TeamOverview } from '@/domain/team-overview.domain.ts';
import {
    teamCurrentTableQueryKey,
    teamFinalRankingQueryKey,
    teamGamesPrefixQueryKey,
    teamOverviewQueryKey,
} from '@/shared/constants/query-keys.ts';
import { useLiveStream } from '@/shared/hooks/use-live-stream.hook.ts';
import { type SseSubscription } from '@/shared/sse/sse-client.ts';
import { TournamentEventType } from '@/vocabulary/tournament-event-type.enum.ts';
import { TournamentStatus } from '@/vocabulary/tournament-status.enum.ts';

/** The query keys each event on the team's live stream makes stale. */
const queryKeysByEvent: Record<TournamentEventType, readonly QueryKey[]> = {
    // A recorded game changes the games of the team's table.
    [TournamentEventType.GAME_RECORDED]: [teamGamesPrefixQueryKey()],
    // A confirmation change moves the table's state and its games.
    [TournamentEventType.TABLE_CONFIRMED]: [teamCurrentTableQueryKey(), teamGamesPrefixQueryKey()],
    [TournamentEventType.CONFIRMATION_RETRACTED]: [
        teamCurrentTableQueryKey(),
        teamGamesPrefixQueryKey(),
    ],
    // A start or a new round changes the overview and the team's table.
    [TournamentEventType.TOURNAMENT_STARTED]: [teamOverviewQueryKey(), teamCurrentTableQueryKey()],
    [TournamentEventType.ROUND_STARTED]: [teamOverviewQueryKey(), teamCurrentTableQueryKey()],
    // Only the overview's own fields change.
    [TournamentEventType.TOTAL_ROUNDS_CHANGED]: [teamOverviewQueryKey()],
    // A finish closes the last table and the overview's status.
    [TournamentEventType.TOURNAMENT_FINISHED]: [
        teamOverviewQueryKey(),
        teamCurrentTableQueryKey(),
        teamGamesPrefixQueryKey(),
    ],
    // The publish brings the status change and the Final Ranking.
    [TournamentEventType.RESULTS_PUBLISHED]: [teamOverviewQueryKey(), teamFinalRankingQueryKey()],
};

const TEAM_SUBSCRIPTIONS: SseSubscription[] = Object.entries(queryKeysByEvent).map(
    ([event, queryKeys]) => ({ event, queryKeys }),
);

/** Every query the team page shows, refetched once per (re)connect to catch up on missed events. */
const TEAM_KEYS_ON_CONNECT: QueryKey[] = [
    teamOverviewQueryKey(),
    teamCurrentTableQueryKey(),
    teamGamesPrefixQueryKey(),
    teamFinalRankingQueryKey(),
];

/**
 * Follows the team's live event stream for the team page and the scoring screen.
 * The stream follows the tournament until its results are published; after that
 * the overview no longer changes (and the Final Ranking is final), so the
 * connection is closed. `live` says whether the stream applies at all, `connected`
 * whether it is up (only meaningful while `live`).
 *
 * Each event refetches the queries it makes stale (see {@link queryKeysByEvent}),
 * and every (re)connect refetches everything shown on the team page, so events
 * missed while offline are caught up.
 */
export function useTeamLiveStream(overview: TeamOverview | undefined): {
    live: boolean;
    connected: boolean;
} {
    const live = overview !== undefined && overview.tournamentStatus !== TournamentStatus.PUBLISHED;
    const connected = useLiveStream({
        url: '/api/team/events',
        enabled: live,
        subscriptions: TEAM_SUBSCRIPTIONS,
        keysOnConnect: TEAM_KEYS_ON_CONNECT,
    });
    return { live, connected };
}
