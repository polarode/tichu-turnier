import { createFileRoute } from '@tanstack/react-router';

import { Dashboard } from '@/features/tournaments/components/dashboard.component.tsx';

export const Route = createFileRoute('/admin/')({
    component: Dashboard,
});
