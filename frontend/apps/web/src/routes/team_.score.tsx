import { useEffect } from 'react';
import { createFileRoute, redirect, useNavigate } from '@tanstack/react-router';
import { useQuery } from '@tanstack/react-query';

import { ScoringPage } from '@/features/scoring/components/scoring-page.component.tsx';
import { teamCurrentTableQueryOptions } from '@/features/scoring/hooks/scoring-queries.hook.ts';
import {
    isMissingTeamSession,
    teamOverviewQueryOptions,
} from '@/features/teams/hooks/team-auth-queries.hook.ts';
import { useTeamLiveStream } from '@/features/teams/hooks/team-live-stream.hook.ts';
import { ApiError } from '@/shared/api/api-error.ts';
import { BackLink } from '@/shared/components/back-link.component.tsx';
import { LoadingIndicator } from '@/shared/components/loading-indicator.component.tsx';
import { PagePlaceholder } from '@/shared/components/page-placeholder.component.tsx';

interface TeamScoreSearch {
    /** Game to open on (`/team/score?game=3`); ignored unless it is on the table's schedule. */
    game?: number;
}

/**
 * The team scoring route (S5c, #101). Parented to the root (a `team_` segment,
 * like `/team/login`) rather than nested under the leaf `/team` overview route,
 * so it renders its own screen. It still runs inside the team session: the
 * current-table load resolves through the httpOnly cookie — a missing team
 * session (see [[isMissingTeamSession]]) bounces to `/team/login`; a 404 means
 * the team is not seated at any round yet (the schedule appears once the
 * tournament starts), shown as a neutral placeholder. The team overview supplies
 * the viewing team's id, so a trio table can mark the games it sits out, and
 * its tournament status, so the screen turns read-only once the tournament is
 * no longer ACTIVE (S7c).
 */
export const Route = createFileRoute('/team_/score')({
    loader: async ({ context }) => {
        try {
            await Promise.all([
                context.queryClient.ensureQueryData(teamCurrentTableQueryOptions),
                context.queryClient.ensureQueryData(teamOverviewQueryOptions),
            ]);
        } catch (error) {
            if (isMissingTeamSession(error)) {
                throw redirect({ to: '/team/login' });
            }
            // A 404 (not seated yet) is handled by the component, not a redirect.
        }
    },
    // A `?game=` link opens that game; keep only a positive whole number, as a number or
    // numeric text. The key stays explicit: the router merges the result over the raw
    // search, so an omitted key would let an invalid ?game= through.
    validateSearch: (search: Record<string, unknown>): TeamScoreSearch => {
        const game = typeof search.game === 'string' ? Number(search.game) : search.game;
        return {
            game: typeof game === 'number' && Number.isInteger(game) && game > 0 ? game : undefined,
        };
    },
    pendingMs: 1,
    pendingComponent: LoadingIndicator,
    component: ScoringRoute,
});

function ScoringRoute() {
    const query = useQuery(teamCurrentTableQueryOptions);
    const overview = useQuery(teamOverviewQueryOptions);
    const { game } = Route.useSearch();
    const navigate = useNavigate();

    useEffect(() => {
        if (isMissingTeamSession(query.error) || isMissingTeamSession(overview.error)) {
            void navigate({ to: '/team/login' });
        }
    }, [navigate, query.error, overview.error]);

    const { live, connected } = useTeamLiveStream(overview.data);

    if (query.isLoading) return <LoadingIndicator />;

    if (query.error instanceof ApiError && query.error.status === 404) {
        return (
            <div className="flex flex-col gap-4">
                <BackLink to="/team" labelKey="scoring.overviewLink" />
                <PagePlaceholder titleKey="scoring.title" descriptionKey="scoring.notSeated" />
            </div>
        );
    }

    if (!query.data || overview.isPending) return <LoadingIndicator />;
    // A failed overview only costs the sit-out markers; scoring itself still works.
    // The tournament status gates every write (S7c): once it is no longer ACTIVE the screen is read-only.
    return (
        <ScoringPage
            table={query.data}
            initialGame={game}
            viewerTeamId={overview.data?.teamId}
            tournamentStatus={overview.data?.tournamentStatus}
            live={live}
            connected={connected}
        />
    );
}
