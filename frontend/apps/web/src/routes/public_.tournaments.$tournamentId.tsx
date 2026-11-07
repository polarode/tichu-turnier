import { createFileRoute } from '@tanstack/react-router';

import { PublicTournamentPage } from '@/features/public-tournaments/components/public-tournament.component.tsx';

/** The public pairings of one tournament. Anonymous, so the route is not guarded. */
function PublicTournamentRoute() {
    const { tournamentId } = Route.useParams();
    return <PublicTournamentPage tournamentId={tournamentId} />;
}

export const Route = createFileRoute('/public_/tournaments/$tournamentId')({
    component: PublicTournamentRoute,
});
