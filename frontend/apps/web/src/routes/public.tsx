import { createFileRoute } from '@tanstack/react-router';

import { PagePlaceholder } from '@/shared/components/page-placeholder.component.tsx';

export const Route = createFileRoute('/public')({
    component: () => (
        <PagePlaceholder titleKey="public.title" descriptionKey="public.description" />
    ),
});
