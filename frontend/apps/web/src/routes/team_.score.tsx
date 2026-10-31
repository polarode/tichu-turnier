import { useEffect } from 'react';
import { createFileRoute, redirect, useNavigate } from '@tanstack/react-router';
import { useQuery } from '@tanstack/react-query';

import { ScoringPage } from '@/features/scoring/components/scoring-page.component.tsx';
import { teamCurrentTableQueryOptions } from '@/features/scoring/hooks/scoring-queries.hook.ts';
import { ApiError } from '@/shared/api/api-error.ts';
import { LoadingIndicator } from '@/shared/components/loading-indicator.component.tsx';
import { PagePlaceholder } from '@/shared/components/page-placeholder.component.tsx';

/**
 * The team scoring route (S5c, #101). Parented to the root (a `team_` segment,
 * like `/team/login`) rather than nested under the leaf `/team` overview route,
 * so it renders its own screen. It still runs inside the team session: the
 * current-table load resolves through the httpOnly cookie — a 401 bounces to
 * `/team/login`; a 404 means the team is not seated at any round yet (the
 * schedule appears once the tournament starts), shown as a neutral placeholder.
 */
export const Route = createFileRoute('/team_/score')({
    loader: async ({ context }) => {
        try {
            await context.queryClient.ensureQueryData(teamCurrentTableQueryOptions);
        } catch (error) {
            if (error instanceof ApiError && error.status === 401) {
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
    const navigate = useNavigate();

    useEffect(() => {
        const error = query.error;
        if (error instanceof ApiError && error.status === 401) {
            void navigate({ to: '/team/login' });
        }
    }, [navigate, query.error]);

    if (query.isLoading) return <LoadingIndicator />;

    if (query.error instanceof ApiError && query.error.status === 404) {
        return (
            <PagePlaceholder titleKey="scoring.title" descriptionKey="scoring.notSeated" />
        );
    }

    if (!query.data) return <LoadingIndicator />;
    return <ScoringPage table={query.data} />;
}
