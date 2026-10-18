import type { QueryClient } from '@tanstack/react-query';
import { createRootRouteWithContext, Outlet } from '@tanstack/react-router';

import { AppShell } from '@/shared/components/app-shell.component.tsx';

export interface RouterContext {
    queryClient: QueryClient;
}

export const Route = createRootRouteWithContext<RouterContext>()({
    component: () => (
        <AppShell>
            <Outlet />
        </AppShell>
    ),
});
