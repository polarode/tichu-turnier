import { render, type RenderResult } from '@testing-library/react';
import {
    createMemoryHistory,
    createRouter,
    type AnyRouter,
    RouterProvider,
} from '@tanstack/react-router';
import { type QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { StrictMode } from 'react';

import { routeTree } from '@/routeTree.gen.ts';
import { I18nProvider } from '@/shared/i18n/i18n.context.tsx';
import { ThemeProvider } from '@/shared/theme/theme.context.tsx';
import type { Locale } from '@/shared/i18n/locale.ts';
import { createTestQueryClient } from '@/test-helpers/render-with-providers.tsx';

export type AppRenderResult = RenderResult & { router: AnyRouter; queryClient: QueryClient };

/**
 * Render the real router (real route tree, in-memory history) so routing and
 * navigation are exercised exactly as they are in the browser.
 */
export function renderApp(
    initialPath = '/',
    {
        initialLocale = 'de',
        strictMode = false,
    }: { initialLocale?: Locale; strictMode?: boolean } = {},
): AppRenderResult {
    const queryClient = createTestQueryClient();
    const router: AnyRouter = createRouter({
        routeTree,
        history: createMemoryHistory({ initialEntries: [initialPath] }),
        context: { queryClient },
    });

    // StrictMode mirrors main.tsx (dev double-invokes mount effects), letting
    // tests reproduce StrictMode-specific races like double-fired effects.
    let app = (
        <ThemeProvider>
            <I18nProvider initialLocale={initialLocale}>
                <QueryClientProvider client={queryClient}>
                    <RouterProvider router={router} />
                </QueryClientProvider>
            </I18nProvider>
        </ThemeProvider>
    );
    if (strictMode) app = <StrictMode>{app}</StrictMode>;

    const result = render(app);

    return { ...result, router, queryClient };
}
