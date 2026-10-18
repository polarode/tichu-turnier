import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { renderApp } from '@/test-helpers/render-app.tsx';

describe('app routing', () => {
    it.each([
        ['/admin', 'Verwaltungsbereich'],
        ['/team', 'Teambereich'],
        ['/public', 'Öffentlicher Bereich'],
        ['/login', 'Anmelden'],
    ])('renders %s with its German heading', async (path, heading) => {
        renderApp(path);

        expect(await screen.findByRole('heading', { name: heading })).toBeInTheDocument();
    });

    it('redirects the root path to the public area', async () => {
        renderApp('/');

        expect(
            await screen.findByRole('heading', { name: 'Öffentlicher Bereich' }),
        ).toBeInTheDocument();
    });
});
