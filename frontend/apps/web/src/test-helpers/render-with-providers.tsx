import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, type RenderResult } from '@testing-library/react';
import type { ReactElement, ReactNode } from 'react';

import { I18nProvider } from '@/shared/i18n/i18n.context.tsx';
import type { Locale } from '@/shared/i18n/locale.ts';

/** A QueryClient with retries off, so tests fail fast and deterministically. */
export function createTestQueryClient(): QueryClient {
    return new QueryClient({
        defaultOptions: {
            queries: { retry: false },
            mutations: { retry: false },
        },
    });
}

interface RenderWithProvidersOptions {
    /** Locale to pin the provider to; defaults to German. */
    initialLocale?: Locale;
}

/** Render a component wrapped in the providers it needs during tests. */
export function renderWithProviders(
    ui: ReactElement,
    { initialLocale = 'de' }: RenderWithProvidersOptions = {},
): RenderResult {
    const queryClient = createTestQueryClient();
    const wrapper = ({ children }: { children: ReactNode }) => (
        <I18nProvider initialLocale={initialLocale}>
            <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
        </I18nProvider>
    );
    return render(ui, { wrapper });
}
