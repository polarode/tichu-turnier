import { createFileRoute } from '@tanstack/react-router';

import { PagePlaceholder } from '@/shared/components/page-placeholder.component.tsx';

export const Route = createFileRoute('/login')({
    component: () => <PagePlaceholder titleKey="login.title" descriptionKey="login.description" />,
});
