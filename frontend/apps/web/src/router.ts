import { QueryClient } from '@tanstack/react-query';
import { createRouter } from '@tanstack/react-router';

import { routeTree } from '@/routeTree.gen.ts';
import { LoadingIndicator } from '@/shared/components/loading-indicator.component.tsx';

export const queryClient = new QueryClient({
    defaultOptions: {
        queries: {
            retry: 1,
            refetchOnWindowFocus: false,
            staleTime: 30_000,
        },
    },
});

export const router = createRouter({
    routeTree,
    context: { queryClient },
    defaultPreload: 'intent',
    // While a guarded route awaits its session check, the route's pending state
    // covers the await so the view never flashes an empty shell.
    defaultPendingComponent: LoadingIndicator,
});

declare module '@tanstack/react-router' {
    interface Register {
        router: typeof router;
    }
}
