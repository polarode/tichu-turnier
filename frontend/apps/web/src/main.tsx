import { QueryClientProvider } from '@tanstack/react-query';
import { RouterProvider } from '@tanstack/react-router';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';

import { queryClient, router } from '@/router.ts';
import { I18nProvider } from '@/shared/i18n/i18n.context.tsx';

import './index.css';

createRoot(document.getElementById('root')!).render(
    <StrictMode>
        <I18nProvider>
            <QueryClientProvider client={queryClient}>
                <RouterProvider router={router} />
            </QueryClientProvider>
        </I18nProvider>
    </StrictMode>,
);
