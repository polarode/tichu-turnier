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

vi.mock('@/features/changelog/changelog.ts', () => ({
    CHANGELOG: [
        {
            date: new Date('2026-11-07T00:00:00Z'),
            changes: [
                { type: 'feature', text: 'Add a changelog page' },
                { type: 'improvement', text: 'Show the ranking faster' },
                { type: 'fix', text: 'Fix the score overflow' },
            ],
        },
    ],
}));

const auth = vi.mocked(AuthRepository);

const plainAdmin = { id: 'admin-1', email: 'organizer@example.com', role: 'ADMIN' as const };

describe('changelog route', () => {
    beforeEach(() => {
        vi.resetAllMocks();
    });

    it('opens the changelog from the avatar menu and lists its entries', async () => {
        auth.refresh.mockResolvedValue(plainAdmin);
        const { router } = renderApp('/public');

        await userEvent.click(await screen.findByRole('button', { name: 'Konto' }));
        await userEvent.click(screen.getByRole('menuitem', { name: 'Änderungen' }));

        await vi.waitFor(() => expect(router.state.location.pathname).toBe('/admin/changelog'));
        expect(screen.queryByRole('menu', { name: 'Konto' })).not.toBeInTheDocument();
        expect(await screen.findByRole('heading', { name: 'Änderungen' })).toBeInTheDocument();

        expect(screen.getByRole('heading', { name: '7.11.2026' })).toBeInTheDocument();
        const items = screen.getAllByRole('listitem');
        expect(items).toHaveLength(3);
        const [feature, improvement, fix] = items as [HTMLElement, HTMLElement, HTMLElement];
        expect(feature).toHaveTextContent('Add a changelog page');
        expect(within(feature).getByText('Feature')).toBeInTheDocument();
        expect(improvement).toHaveTextContent('Show the ranking faster');
        expect(within(improvement).getByText('Improvement')).toBeInTheDocument();
        expect(fix).toHaveTextContent('Fix the score overflow');
        expect(within(fix).getByText('Fix')).toBeInTheDocument();
    });

    it('redirects signed-out visitors from /admin/changelog to login', async () => {
        auth.refresh.mockRejectedValue(
            new ApiError({
                code: 'UNAUTHENTICATED',
                status: 401,
                field: null,
                message: 'no session',
            }),
        );

        renderApp('/admin/changelog');

        expect(await screen.findByRole('heading', { name: 'Anmelden' })).toBeInTheDocument();
    });
});
