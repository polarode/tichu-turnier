import { render, type RenderResult } from '@testing-library/react';
import { createMemoryHistory, createRouter, RouterProvider } from '@tanstack/react-router';
import { QueryClientProvider } from '@tanstack/react-query';

import { routeTree } from '@/routeTree.gen.ts';
import { I18nProvider } from '@/shared/i18n/i18n.context.tsx';
import type { Locale } from '@/shared/i18n/locale.ts';
import { createTestQueryClient } from '@/test-helpers/render-with-providers.tsx';

/**
 * Render the real router (real route tree, in-memory history) so routing and
 * navigation are exercised exactly as they are in the browser.
 */
export function renderApp(
    initialPath = '/',
    { initialLocale = 'de' }: { initialLocale?: Locale } = {},
): RenderResult {
    const queryClient = createTestQueryClient();
    const router = createRouter({
        routeTree,
        history: createMemoryHistory({ initialEntries: [initialPath] }),
        context: { queryClient },
    });

    const result = render(
        <I18nProvider initialLocale={initialLocale}>
            <QueryClientProvider client={queryClient}>
                <RouterProvider router={router} />
            </QueryClientProvider>
        </I18nProvider>,
    );

    return result;
}
