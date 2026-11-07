import { useEffect } from 'react';
import { createFileRoute, redirect, useNavigate } from '@tanstack/react-router';
import { useQuery } from '@tanstack/react-query';

import {
    ensureTeamOverview,
    isMissingTeamSession,
    teamOverviewQueryOptions,
} from '@/features/teams/hooks/team-auth-queries.hook.ts';
import { useTeamLiveStream } from '@/features/teams/hooks/team-live-stream.hook.ts';
import { TeamOverview } from '@/features/teams/components/team-overview.component.tsx';
import { LoadingIndicator } from '@/shared/components/loading-indicator.component.tsx';
import { teamOverviewQueryKey } from '@/shared/constants/query-keys.ts';

export const Route = createFileRoute('/team')({
    // The httpOnly team-session cookie is the only session store: both the guard
    // and the loader resolve through `GET /api/team/me`; there is no client session
    // cache. `beforeLoad` redirects on a cached 401; the loader awaits the check so
    // the route's pendingComponent covers the await with a spinner.
    beforeLoad: ({ context }) => {
        if (context.queryClient.getQueryData(teamOverviewQueryKey()) === null) {
            throw redirect({ to: '/team/login' });
        }
    },
    loader: async ({ context }) => {
        const overview = await ensureTeamOverview(context.queryClient);
        if (overview === null) {
            throw redirect({ to: '/team/login' });
        }
        return overview;
    },
    pendingMs: 1,
    pendingComponent: LoadingIndicator,
    component: TeamOverviewPage,
});

function TeamOverviewPage() {
    const query = useQuery(teamOverviewQueryOptions);
    const navigate = useNavigate();

    // A failure after the initial load means the team session died mid-flight
    // (see isMissingTeamSession); bounce to the neutral login prompt rather than
    // showing an error.
    useEffect(() => {
        if (isMissingTeamSession(query.error)) {
            void navigate({ to: '/team/login' });
        }
    }, [navigate, query.error]);

    const { live, connected } = useTeamLiveStream(query.data);

    if (!query.data) return <LoadingIndicator />;
    return <TeamOverview overview={query.data} live={live} connected={connected} />;
}
