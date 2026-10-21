import { screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { TeamAuthRepository } from '@/team-auth-api/team-auth.repository.ts';
import { ApiError } from '@/shared/api/api-error.ts';
import { renderApp } from '@/test-helpers/render-app.tsx';

vi.mock('@/team-auth-api/team-auth.repository.ts', () => ({
    TeamAuthRepository: {
        exchange: vi.fn(),
        me: vi.fn(),
    },
}));

const mockedMe = vi.mocked(TeamAuthRepository.me);

const overview = {
    teamId: 'team-1',
    tournamentId: 't-1',
    tournamentName: 'Spring Classic',
    teamName: 'Team Alpha',
    players: [
        { id: 'p-1', name: 'Alice' },
        { id: 'p-2', name: 'Bob' },
    ],
};

describe('/team', () => {
    beforeEach(() => {
        mockedMe.mockReset();
    });

    it('renders tournament name, team name, both players and the neutral schedule note', async () => {
        mockedMe.mockResolvedValue(overview);

        const { router } = renderApp('/team');

        await waitFor(() =>
            expect(router.state.location.pathname).toBe('/team'),
        );
        expect(await screen.findByText('Spring Classic')).toBeInTheDocument();
        expect(screen.getByText('Team Alpha')).toBeInTheDocument();
        expect(screen.getByText('Alice')).toBeInTheDocument();
        expect(screen.getByText('Bob')).toBeInTheDocument();
        expect(screen.getByText('Der Spielplan erscheint, sobald das Turnier gestartet ist.')).toBeInTheDocument();
        expect(mockedMe).toHaveBeenCalledTimes(1);
    });

    it('redirects to the neutral /team/login prompt when no session exists', async () => {
        mockedMe.mockRejectedValue(
            new ApiError({
                code: 'UNAUTHENTICATED',
                status: 401,
                field: null,
                message: 'session gone',
            }),
        );

        const { router } = renderApp('/team');

        await waitFor(() =>
            expect(router.state.location.pathname).toBe('/team/login'),
        );
        expect(
            await screen.findByText(
                'Öffne den persönlichen Zugriffslink aus der Nachricht deines Turnier-Organisators.',
            ),
        ).toBeInTheDocument();
    });

    it('shows the router pending spinner while the session check is in flight', async () => {
        let resolveMe!: (value: typeof overview) => void;
        mockedMe.mockImplementation(() => new Promise((resolve) => (resolveMe = resolve)));

        const { router } = renderApp('/public');
        await screen.findByText('Öffentlicher Bereich');
        expect(router.state.location.pathname).toBe('/public');

        // Direct navigation (no hover, so the intent-preload cannot pre-warm the
        // cache): the pending state must cover the /me await with the spinner.
        void router.navigate({ to: '/team' });
        expect(await screen.findByText('Wird geladen…')).toBeInTheDocument();
        resolveMe(overview);
        await screen.findByText('Spring Classic');
        expect(screen.queryByText('Wird geladen…')).not.toBeInTheDocument();
        expect(router.state.location.pathname).toBe('/team');
    });
});
