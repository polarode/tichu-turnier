import { createFileRoute } from '@tanstack/react-router';

import { PagePlaceholder } from '@/shared/components/page-placeholder.component.tsx';

export const Route = createFileRoute('/admin')({
    component: () => (
        <PagePlaceholder titleKey="admin.title" descriptionKey="admin.description" />
    ),
});
