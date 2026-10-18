import { createFileRoute } from '@tanstack/react-router';

import { PagePlaceholder } from '@/shared/components/page-placeholder.component.tsx';

/** Placeholder for the create form; replaced in #17. */
export const Route = createFileRoute('/admin/tournaments/new')({
    component: () => (
        <PagePlaceholder
            titleKey="tournament.new.title"
            descriptionKey="tournament.new.description"
        />
    ),
});
