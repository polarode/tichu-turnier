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
        expect(screen.getByRole('link', { name: 'Anmelden' })).toHaveAttribute('href', '/login');
    });

    it('switches every visible string to English and updates <html lang>', async () => {
        renderApp('/login', { initialLocale: 'de' });

        expect(await screen.findByRole('heading', { name: 'Anmelden' })).toBeInTheDocument();

        await userEvent.click(screen.getByRole('button', { name: 'Englisch' }));

        expect(await screen.findByText('Tichu Tournament')).toBeInTheDocument();
        expect(screen.getByRole('heading', { name: 'Login' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'English' })).toBeInTheDocument();
        expect(document.documentElement.lang).toBe('en');
        expect(document.title).toBe('Tichu Tournament');
    });
});
