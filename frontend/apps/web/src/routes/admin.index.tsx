import { createFileRoute } from '@tanstack/react-router';

import { PagePlaceholder } from '@/shared/components/page-placeholder.component.tsx';

/** Admin landing placeholder; the tournament dashboard replaces this in #16. */
export const Route = createFileRoute('/admin/')({
    component: () => (
        <PagePlaceholder titleKey="admin.title" descriptionKey="admin.description" />
    ),
});
