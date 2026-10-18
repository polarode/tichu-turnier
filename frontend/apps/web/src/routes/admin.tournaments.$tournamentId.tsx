import { createFileRoute } from '@tanstack/react-router';

import { PagePlaceholder } from '@/shared/components/page-placeholder.component.tsx';

/** Placeholder for the tournament detail / team-management screen; replaced in #18. */
export const Route = createFileRoute('/admin/tournaments/$tournamentId')({
    component: () => (
        <PagePlaceholder
            titleKey="tournament.detail.title"
            descriptionKey="tournament.detail.description"
        />
    ),
});
