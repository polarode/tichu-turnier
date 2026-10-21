import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { TeamAuthRepository } from '@/team-auth-api/team-auth.repository.ts';
import { renderApp } from '@/test-helpers/render-app.tsx';

vi.mock('@/team-auth-api/team-auth.repository.ts', () => ({
    TeamAuthRepository: {
        exchange: vi.fn(),
        me: vi.fn(),
    },
}));

const mockedExchange = vi.mocked(TeamAuthRepository.exchange);
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

describe('/team/login', () => {
    beforeEach(() => {
        mockedExchange.mockReset();
        mockedMe.mockReset();
        mockedMe.mockResolvedValue(overview);
    });

    it('strips the token from the URL immediately and redirects to /team after a successful exchange', async () => {
        mockedExchange.mockResolvedValue({
            teamId: 'team-1',
            tournamentId: 't-1',
            expiresAt: new Date('2026-01-01T10:00:00Z'),
        });

        // The token must be gone from the address bar before the exchange resolves.
        let searchDuringExchange: string | null = null;
        mockedExchange.mockImplementation(() => {
            searchDuringExchange = JSON.stringify(router.state.location.search);
            return Promise.resolve({
                teamId: 'team-1',
                tournamentId: 't-1',
                expiresAt: new Date('2026-01-01T10:00:00Z'),
            });
        });

        const { router } = renderApp('/team/login?token=raw-secret-token');

        await waitFor(() => expect(searchDuringExchange).not.toBeNull());
        expect(searchDuringExchange).not.toContain('raw-secret-token');
        await waitFor(() =>
            expect(screen.getByText('Teambereich')).toBeInTheDocument(),
        );
        expect(router.state.location.pathname).toBe('/team');
        expect(mockedExchange).toHaveBeenCalledWith('raw-secret-token');
        expect(router.state.location.search).not.toContain('raw-secret-token');
    });

    it('shows the same dead-end error screen for any rejected link, with a way back to the start page', async () => {
        mockedExchange.mockRejectedValue(
            Object.assign(new Error('rejected'), { code: 'TEAM_ACCESS_INVALID' }),
        );

        renderApp('/team/login?token=dead-token');

        await screen.findByText(
            'Dieser Zugriffslink ist ungültig oder nicht mehr aktiv. Bitte wende dich an deinen Turnier-Organisator.',
        );
        expect(screen.queryByText(/Erneut versuchen/i)).not.toBeInTheDocument();
        expect(mockedExchange).toHaveBeenCalledWith('dead-token');

        await userEvent.click(screen.getByText('Zur Startseite'));
        await waitFor(() => expect(screen.getByText('Öffentlicher Bereich')).toBeInTheDocument());
    });

    it('shows the neutral prompt when no token is present, not an error', async () => {
        renderApp('/team/login');

        expect(
            await screen.findByText(
                'Öffne den persönlichen Zugriffslink aus der Nachricht deines Turnier-Organisators.',
            ),
        ).toBeInTheDocument();
        expect(mockedExchange).not.toHaveBeenCalled();
    });
});
