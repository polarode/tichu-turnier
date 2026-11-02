import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { TeamAuthRepository } from '@/team-auth-api/team-auth.repository.ts';
import { ScoringRepository } from '@/scoring-api/scoring.repository.ts';
import { ApiError } from '@/shared/api/api-error.ts';
import { TournamentStatus } from '@/vocabulary/tournament-status.enum.ts';
import { renderApp } from '@/test-helpers/render-app.tsx';

vi.mock('@/team-auth-api/team-auth.repository.ts', () => ({
    TeamAuthRepository: {
        exchange: vi.fn(),
        me: vi.fn(),
    },
}));

vi.mock('@/scoring-api/scoring.repository.ts', () => ({
    ScoringRepository: {
        currentTable: vi.fn(),
        games: vi.fn().mockResolvedValue([]),
        create: vi.fn(),
        update: vi.fn(),
    },
}));

const mockedMe = vi.mocked(TeamAuthRepository.me);
const mockedCurrentTable = vi.mocked(ScoringRepository.currentTable);

const overview = {
    teamId: 'team-1',
    tournamentId: 't-1',
    tournamentName: 'Spring Classic',
    tournamentStatus: TournamentStatus.ACTIVE,
    teamName: 'Team Alpha',
    players: [
        { id: 'p-1', name: 'Alice' },
        { id: 'p-2', name: 'Bob' },
    ],
};

describe('/team', () => {
    beforeEach(() => {
        mockedMe.mockReset();
        mockedCurrentTable.mockReset();
    });

    it('renders tournament name, team name, both players and the neutral schedule note while in setup', async () => {
        mockedMe.mockResolvedValue({ ...overview, tournamentStatus: TournamentStatus.SETUP });

        const { router } = renderApp('/team');

        await waitFor(() =>
            expect(router.state.location.pathname).toBe('/team'),
        );
        expect(await screen.findByText('Spring Classic')).toBeInTheDocument();
        expect(screen.getByText('Team Alpha')).toBeInTheDocument();
        expect(screen.getByText('Alice')).toBeInTheDocument();
        expect(screen.getByText('Bob')).toBeInTheDocument();
        expect(screen.getByText('Der Spielplan erscheint, sobald das Turnier gestartet ist.')).toBeInTheDocument();
        expect(screen.queryByRole('link', { name: 'Ergebnisse eintragen' })).not.toBeInTheDocument();
        expect(mockedMe).toHaveBeenCalledTimes(1);
    });

    it('swaps the schedule note for the scoring link once the tournament has started', async () => {
        mockedMe.mockResolvedValue({ ...overview, tournamentStatus: TournamentStatus.ACTIVE });

        const { router } = renderApp('/team');

        await waitFor(() => expect(router.state.location.pathname).toBe('/team'));
        expect(await screen.findByRole('link', { name: 'Ergebnisse eintragen' })).toBeInTheDocument();
        expect(screen.queryByText('Der Spielplan erscheint, sobald das Turnier gestartet ist.')).not.toBeInTheDocument();
    });

    it('keeps the scoring link (and no schedule note) after the tournament has completed', async () => {
        mockedMe.mockResolvedValue({ ...overview, tournamentStatus: TournamentStatus.COMPLETED });

        const { router } = renderApp('/team');

        await waitFor(() => expect(router.state.location.pathname).toBe('/team'));
        expect(await screen.findByRole('link', { name: 'Ergebnisse eintragen' })).toBeInTheDocument();
        expect(screen.queryByText('Der Spielplan erscheint, sobald das Turnier gestartet ist.')).not.toBeInTheDocument();
    });

    it('links to the scoring screen, which loads the team’s current table', async () => {
        mockedMe.mockResolvedValue(overview);
        // The overview link renders unconditionally; the scoring route resolves
        // the seat itself (a 404 there would show the not-seated placeholder).
        mockedCurrentTable.mockResolvedValue({
            tournamentId: 't-1',
            roundNumber: 1,
            tableNumber: 1,
            locked: false,
            allowGrandTichu: true,
            teams: [
                {
                    teamId: 'team-1',
                    teamName: 'Team Alpha',
                    players: [
                        { playerId: 'p-1', name: 'Alice' },
                        { playerId: 'p-2', name: 'Bob' },
                    ],
                },
                {
                    teamId: 'team-2',
                    teamName: 'Team Beta',
                    players: [
                        { playerId: 'p-3', name: 'Carol' },
                        { playerId: 'p-4', name: 'Dave' },
                    ],
                },
            ],
            trio: false,
            schedule: [1, 2, 3, 4].map((gameNumber) => ({ gameNumber, teamIds: ['team-1', 'team-2'] })),
        });

        const { router } = renderApp('/team');
        await screen.findByText('Spring Classic');

        await userEvent.click(screen.getByRole('link', { name: 'Ergebnisse eintragen' }));

        await waitFor(() => expect(router.state.location.pathname).toBe('/team/score'));
        expect(await screen.findByText('Team Alpha gegen Team Beta')).toBeInTheDocument();
        expect(mockedCurrentTable).toHaveBeenCalled();
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

    it('redirects to /team/login on 403 (admin session in the same browser, but no team cookie)', async () => {
        mockedMe.mockRejectedValue(
            new ApiError({
                code: 'FORBIDDEN',
                status: 403,
                field: null,
                message: 'access denied',
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
