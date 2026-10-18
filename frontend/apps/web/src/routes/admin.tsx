import { createFileRoute, redirect } from '@tanstack/react-router';

import { AdminLayout } from '@/features/auth/components/admin-layout.component.tsx';
import { sessionQueryOptions } from '@/features/auth/hooks/auth-queries.hook.ts';

/**
 * Guarded admin area. `beforeLoad` runs for every child route, so resolving the
 * session once here protects the whole subtree: unauthenticated visitors are sent to
 * login before any admin screen renders.
 */
export const Route = createFileRoute('/admin')({
    beforeLoad: async ({ context }) => {
        const session = await context.queryClient.ensureQueryData(sessionQueryOptions);
        if (session === null) {
            throw redirect({ to: '/login' });
        }
    },
    component: AdminLayout,
});
