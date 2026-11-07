import { createFileRoute } from '@tanstack/react-router';

import { PublicHome } from '@/features/public-tournaments/components/public-home.component.tsx';

export const Route = createFileRoute('/public')({
    component: PublicHome,
});
