import { createFileRoute } from '@tanstack/react-router';

import { TeamLogin } from '@/features/teams/components/team-login.component.tsx';

interface TeamLoginSearch {
    /** Raw access-link token from a team's magic link (`/team/login?token=…`). */
    token?: string;
}

function TeamLoginPage() {
    const { token } = Route.useSearch();
    return <TeamLogin token={token} />;
}

export const Route = createFileRoute('/team_/login')({
    // Access links land here with `?token=<raw>`; ignore anything empty or non-text.
    validateSearch: (search: Record<string, unknown>): TeamLoginSearch =>
        typeof search.token === 'string' && search.token.length > 0
            ? { token: search.token }
            : {},
    component: TeamLoginPage,
});
