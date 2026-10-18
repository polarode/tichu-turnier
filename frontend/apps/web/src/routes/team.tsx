import { createFileRoute } from '@tanstack/react-router';

import { PagePlaceholder } from '@/shared/components/page-placeholder.component.tsx';

export const Route = createFileRoute('/team')({
    component: () => <PagePlaceholder titleKey="team.title" descriptionKey="team.description" />,
});
