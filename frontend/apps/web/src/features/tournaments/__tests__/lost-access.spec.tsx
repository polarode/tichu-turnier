import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthRepository } from '@/auth-api/auth.repository.ts';
import { ApiError } from '@/shared/api/api-error.ts';
import { TeamRepository } from '@/team-api/team.repository.ts';
import { TournamentRepository } from '@/tournament-api/tournament.repository.ts';
import { renderApp } from '@/test-helpers/render-app.tsx';

vi.mock('@/auth-api/auth.repository.ts', () => ({
    AuthRepository: { register: vi.fn(), login: vi.fn(), refresh: vi.fn(), logout: vi.fn() },
}));
vi.mock('@/tournament-api/tournament.repository.ts', async () => {
    const { mockTournamentRepositoryModule } =
        await import('@/test-helpers/tournament-repository.mock.ts');
    return mockTournamentRepositoryModule();
});

vi.mock('@/team-api/team.repository.ts', () => ({
    TeamRepository: {
        list: vi.fn(),
        get: vi.fn(),
        create: vi.fn(),
        update: vi.fn(),
        remove: vi.fn(),
    },
}));

const auth = vi.mocked(AuthRepository);
const tournaments = vi.mocked(TournamentRepository);
const teams = vi.mocked(TeamRepository);

const admin = { id: 'admin-1', email: '[EMAIL]', role: 'ADMIN' as const };
const LOST_ACCESS = 'You no longer have access to this tournament.';

beforeEach(() => {
    vi.clearAllMocks();
    auth.refresh.mockResolvedValue(admin);
    teams.list.mockResolvedValue([]);
    tournaments.list.mockResolvedValue([]);
});

describe('lost access to a tournament', () => {
    it('sends the admin to the dashboard with a dismissible notice', async () => {
        tournaments.get.mockRejectedValue(
            new ApiError({ code: 'NOT_FOUND', status: 404, field: null, message: 'gone' }),
        );
        const { router } = renderApp('/admin/tournaments/t-1', { initialLocale: 'en' });

        expect(await screen.findByText(LOST_ACCESS)).toBeInTheDocument();
        expect(router.state.location.pathname).toBe('/admin');
        expect(screen.getByRole('status')).toHaveTextContent(LOST_ACCESS);

        await userEvent.click(screen.getByRole('button', { name: 'Dismiss' }));

        expect(screen.queryByText(LOST_ACCESS)).not.toBeInTheDocument();
    });

    it('keeps other failures on the tournament page as an inline error', async () => {
        tournaments.get.mockRejectedValue(
            new ApiError({ code: 'UNKNOWN', status: 500, field: null, message: 'boom' }),
        );
        const { router } = renderApp('/admin/tournaments/t-1', { initialLocale: 'en' });

        expect(await screen.findByRole('alert')).toBeInTheDocument();
        await waitFor(() => expect(router.state.location.pathname).toBe('/admin/tournaments/t-1'));
        expect(screen.queryByText(LOST_ACCESS)).not.toBeInTheDocument();
    });
});
