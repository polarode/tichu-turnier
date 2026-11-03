import { useEffect } from 'react';
import { createFileRoute, redirect, useNavigate } from '@tanstack/react-router';
import { useQuery } from '@tanstack/react-query';

import { ScoringPage } from '@/features/scoring/components/scoring-page.component.tsx';
import { teamCurrentTableQueryOptions } from '@/features/scoring/hooks/scoring-queries.hook.ts';
import { isMissingTeamSession, teamOverviewQueryOptions } from '@/features/teams/hooks/team-auth-queries.hook.ts';
import { ApiError } from '@/shared/api/api-error.ts';
import { BackLink } from '@/shared/components/back-link.component.tsx';
import { LoadingIndicator } from '@/shared/components/loading-indicator.component.tsx';
import { PagePlaceholder } from '@/shared/components/page-placeholder.component.tsx';

/**
 * The team scoring route (S5c, #101). Parented to the root (a `team_` segment,
 * like `/team/login`) rather than nested under the leaf `/team` overview route,
 * so it renders its own screen. It still runs inside the team session: the
 * current-table load resolves through the httpOnly cookie — a missing team
 * session (see [[isMissingTeamSession]]) bounces to `/team/login`; a 404 means
 * the team is not seated at any round yet (the schedule appears once the
 * tournament starts), shown as a neutral placeholder. The team overview supplies
 * the viewing team's id, so a trio table can mark the games it sits out.
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
    pendingMs: 1,
    pendingComponent: LoadingIndicator,
    component: ScoringRoute,
});

function ScoringRoute() {
    const query = useQuery(teamCurrentTableQueryOptions);
    const overview = useQuery(teamOverviewQueryOptions);
    const navigate = useNavigate();

    useEffect(() => {
        if (isMissingTeamSession(query.error) || isMissingTeamSession(overview.error)) {
            void navigate({ to: '/team/login' });
        }
    }, [navigate, query.error, overview.error]);

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
    return <ScoringPage table={query.data} viewerTeamId={overview.data?.teamId} />;
}
