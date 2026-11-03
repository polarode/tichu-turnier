import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import { renderApp } from '@/test-helpers/render-app.tsx';

describe('AppShell', () => {
    it('shows the German shell with navigation to every area', async () => {
        renderApp('/public', { initialLocale: 'de' });

        expect(await screen.findByText('Tichu Turnier')).toBeInTheDocument();
        expect(document.title).toBe('Tichu Turnier');

        const nav = screen.getByRole('navigation', { name: 'Hauptnavigation' });
        expect(within(nav).getByRole('link', { name: 'Verwaltung' })).toHaveAttribute(
            'href',
            '/admin',
        );
        expect(within(nav).getByRole('link', { name: 'Team' })).toHaveAttribute('href', '/team');
        expect(within(nav).getByRole('link', { name: 'Öffentlich' })).toHaveAttribute(
            'href',
            '/public',
        );
        // The active area is announced, not only shown by its suit-coloured bar.
        expect(within(nav).getByRole('link', { name: 'Öffentlich' })).toHaveAttribute('aria-current', 'page');
        expect(within(nav).getByRole('link', { name: 'Team' })).not.toHaveAttribute('aria-current');
        // Signed-out visitors see neither a login entry nor an avatar in the shell.
        expect(screen.queryByRole('link', { name: 'Anmelden' })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Konto' })).not.toBeInTheDocument();
    });

    it('switches every visible string to English and updates <html lang>', async () => {
        renderApp('/login', { initialLocale: 'de' });

        expect(await screen.findByRole('heading', { name: 'Anmelden' })).toBeInTheDocument();

        await userEvent.click(screen.getByRole('button', { name: 'Sprache' }));
        await userEvent.click(screen.getByRole('menuitemradio', { name: 'Englisch' }));

        expect(await screen.findByText('Tichu Tournament')).toBeInTheDocument();
        expect(screen.getByRole('heading', { name: 'Login' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Language' })).toBeInTheDocument();
        expect(document.documentElement.lang).toBe('en');
        expect(document.title).toBe('Tichu Tournament');
    });
});
