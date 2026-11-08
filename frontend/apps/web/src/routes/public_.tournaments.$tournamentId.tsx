import { createFileRoute } from '@tanstack/react-router';

import {
    PublicTournamentPage,
    type PublicTab,
} from '@/features/public-tournaments/components/public-tournament.component.tsx';

/** The tabs of a published tournament, switched by tabs and kept in the URL. */
const PUBLIC_TABS: readonly PublicTab[] = ['ranking', 'rounds', 'statistics'];

function isPublicTab(value: unknown): value is PublicTab {
    return PUBLIC_TABS.some((tab) => tab === value);
}

interface PublicTournamentSearch {
    tab?: PublicTab;
}

/** The public pairings of one tournament. Anonymous, so the route is not guarded. */
function PublicTournamentRoute() {
    const { tournamentId } = Route.useParams();
    const { tab = 'ranking' } = Route.useSearch();
    const navigate = Route.useNavigate();
    return (
        <PublicTournamentPage
            tournamentId={tournamentId}
            tab={tab}
            onTabChange={(next) => void navigate({ search: { tab: next }, replace: true })}
        />
    );
}

export const Route = createFileRoute('/public_/tournaments/$tournamentId')({
    // An explicit undefined: the router merges the result over the raw search, so an
    // omitted key would let an unknown ?tab= through.
    validateSearch: (search: Record<string, unknown>): PublicTournamentSearch => ({
        tab: isPublicTab(search.tab) ? search.tab : undefined,
    }),
    component: PublicTournamentRoute,
});
