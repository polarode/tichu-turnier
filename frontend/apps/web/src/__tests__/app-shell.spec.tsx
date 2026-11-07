import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthRepository } from '@/auth-api/auth.repository.ts';
import { ApiError } from '@/shared/api/api-error.ts';
import { renderApp } from '@/test-helpers/render-app.tsx';

vi.mock('@/auth-api/auth.repository.ts', () => ({
    AuthRepository: {
        register: vi.fn(),
        login: vi.fn(),
        refresh: vi.fn(),
        logout: vi.fn(),
    },
}));

const auth = vi.mocked(AuthRepository);
const admin = { id: 'admin-1', email: 'organizer@example.com', role: 'ADMIN' as const };

describe('AppShell', () => {
    beforeEach(() => {
        vi.resetAllMocks();
        auth.refresh.mockRejectedValue(
            new ApiError({ code: 'UNAUTHENTICATED', status: 401, field: null, message: 'no session' }),
        );
    });

    it('shows a signed-in admin the German shell with navigation to every area', async () => {
        auth.refresh.mockResolvedValue(admin);
        renderApp('/public', { initialLocale: 'de' });

        expect(await screen.findByText('Tichu Turnier')).toBeInTheDocument();
        expect(document.title).toBe('Tichu Turnier');

        const nav = screen.getByRole('navigation', { name: 'Hauptnavigation' });
        expect(await within(nav).findByRole('link', { name: 'Verwaltung' })).toHaveAttribute(
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
    });

    it('hides the admin area from signed-out visitors', async () => {
        renderApp('/public');

        const nav = await screen.findByRole('navigation', { name: 'Hauptnavigation' });
        await vi.waitFor(() => expect(auth.refresh).toHaveBeenCalled());
        expect(within(nav).getByRole('link', { name: 'Team' })).toBeInTheDocument();
        expect(within(nav).getByRole('link', { name: 'Öffentlich' })).toBeInTheDocument();
        expect(within(nav).queryByRole('link', { name: 'Verwaltung' })).not.toBeInTheDocument();
        // Sign-in moves to the corner where a session's avatar would sit.
        expect(await screen.findByRole('button', { name: 'Als Organisator anmelden' })).toBeInTheDocument();
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
