import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { TeamAuthRepository } from '@/team-auth-api/team-auth.repository.ts';
import { ScoringRepository } from '@/scoring-api/scoring.repository.ts';
import type { RecordedGame, TeamTable } from '@/domain/game.domain.ts';
import { ApiError } from '@/shared/api/api-error.ts';
import { TournamentStatus } from '@/vocabulary/tournament-status.enum.ts';
import { renderApp } from '@/test-helpers/render-app.tsx';
import { bodyRows, columnHeaders } from '@/test-helpers/table-rows.ts';

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
        confirm: vi.fn(),
        retract: vi.fn(),
    },
}));

const mockedMe = vi.mocked(TeamAuthRepository.me);
const mockedCurrentTable = vi.mocked(ScoringRepository.currentTable);
const mockedGames = vi.mocked(ScoringRepository.games);
const mockedConfirm = vi.mocked(ScoringRepository.confirm);
const mockedRetract = vi.mocked(ScoringRepository.retract);

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
        mockedGames.mockReset();
        mockedGames.mockResolvedValue([]);
        mockedConfirm.mockReset();
        mockedRetract.mockReset();
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
            confirmation: { status: 'PLAYING', confirmedTeamIds: [] },
            grandTichuBonus: 200,
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

// ---- confirmation panel (S6b, #106) ------------------------------------------

function seatedTable(overrides: Partial<TeamTable> = {}): TeamTable {
    return {
        tournamentId: 't-1',
        roundNumber: 1,
        tableNumber: 2,
        confirmation: { status: 'PLAYING', confirmedTeamIds: [] },
        grandTichuBonus: 200,
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
        ...overrides,
    };
}

const trioTable = (confirmedTeamIds: string[] = []): TeamTable =>
    seatedTable({
        confirmation: { status: confirmedTeamIds.length ? 'CONFIRMING' : 'PLAYING', confirmedTeamIds },
        teams: [
            seatedTable().teams[0],
            seatedTable().teams[1],
            {
                teamId: 'team-3',
                teamName: 'Team Gamma',
                players: [
                    { playerId: 'p-5', name: 'Eve' },
                    { playerId: 'p-6', name: 'Finn' },
                ],
            },
        ],
        trio: true,
        schedule: [
            { gameNumber: 1, teamIds: ['team-1', 'team-2'] },
            { gameNumber: 2, teamIds: ['team-1', 'team-3'] },
            { gameNumber: 3, teamIds: ['team-2', 'team-3'] },
            { gameNumber: 4, teamIds: ['team-1', 'team-2'] },
            { gameNumber: 5, teamIds: ['team-1', 'team-3'] },
            { gameNumber: 6, teamIds: ['team-2', 'team-3'] },
        ],
    });

/** Team Alpha (first team) wins 60 : 40 base, no bonuses. */
function game(gameNumber: number, version = 1, teamIds: [string, string] = ['team-1', 'team-2']): RecordedGame {
    return {
        gameNumber,
        version,
        beschiss: false,
        teams: [
            {
                teamId: teamIds[0],
                baseScore: 60,
                players: [
                    { playerId: 'x1', position: 1, call: 'NONE', bombs: 0, tichuSuccess: false },
                    { playerId: 'x2', position: 3, call: 'NONE', bombs: 0, tichuSuccess: false },
                ],
            },
            {
                teamId: teamIds[1],
                baseScore: 40,
                players: [
                    { playerId: 'y1', position: 2, call: 'NONE', bombs: 0, tichuSuccess: false },
                    { playerId: 'y2', position: 4, call: 'NONE', bombs: 0, tichuSuccess: false },
                ],
            },
        ],
        outcome: {
            tichuSuccesses: [],
            doubleWinTeam: null,
            teamPoints: { [teamIds[0]]: 60, [teamIds[1]]: 40 },
            victoryPoints: { [teamIds[0]]: 2, [teamIds[1]]: 0 },
        },
    };
}

const allGames = () => [1, 2, 3, 4].map((n) => game(n));

async function renderPanel(table: TeamTable, games: RecordedGame[]) {
    mockedMe.mockResolvedValue(overview);
    mockedCurrentTable.mockResolvedValue(table);
    mockedGames.mockResolvedValue(games);
    const result = renderApp('/team');
    const panel = await screen.findByRole('region', { name: 'Runde 1 · Tisch B' });
    // The summary renders once the games are in.
    await within(panel).findByRole('table', { name: 'Spiele' });
    return { ...result, panel };
}

describe('/team confirmation panel', () => {
    beforeEach(() => {
        mockedMe.mockReset();
        mockedCurrentTable.mockReset();
        mockedGames.mockReset();
        mockedConfirm.mockReset();
        mockedRetract.mockReset();
    });

    it('summarizes entered and missing games and keeps Confirm disabled with a hint until all are in', async () => {
        const { panel } = await renderPanel(seatedTable(), [game(1), game(3)]);

        expect(bodyRows(within(panel).getByRole('table', { name: 'Spiele' }))).toEqual([
            ['1', '60', '40', ''],
            ['2', '–', '–', 'fehlt'],
            ['3', '60', '40', ''],
            ['4', '–', '–', 'fehlt'],
        ]);
        expect(within(panel).getByText('Status: Spielen · 0 von 2 bestätigt')).toBeInTheDocument();
        expect(within(panel).getByRole('button', { name: 'Alle Spiele bestätigen' })).toBeDisabled();
        expect(within(panel).getByText('2 von 4 Spielen eingetragen')).toBeInTheDocument();
        // The scoring link stays below the panel.
        expect(screen.getByRole('link', { name: 'Ergebnisse eintragen' })).toBeInTheDocument();
    });

    it('confirms through the dialog with the versions the team saw', async () => {
        const user = userEvent.setup();
        const games = [game(1, 3), game(2), game(3), game(4, 2)];
        const { panel } = await renderPanel(seatedTable(), games);
        mockedConfirm.mockResolvedValue({ status: 'CONFIRMING', confirmedTeamIds: ['team-1'] });

        await user.click(within(panel).getByRole('button', { name: 'Alle Spiele bestätigen' }));
        const dialog = await screen.findByRole('dialog', { name: 'Alle Spiele bestätigen?' });
        mockedCurrentTable.mockResolvedValue(
            seatedTable({ confirmation: { status: 'CONFIRMING', confirmedTeamIds: ['team-1'] } }),
        );
        await user.click(within(dialog).getByRole('button', { name: 'Bestätigen' }));

        await waitFor(() => expect(mockedConfirm).toHaveBeenCalledTimes(1));
        expect(mockedConfirm.mock.calls[0]![0]).toMatchObject({ tournamentId: 't-1', roundNumber: 1, tableNumber: 2 });
        expect(mockedConfirm.mock.calls[0]![1]).toEqual(games);
        expect(await within(panel).findByText('Warten auf das andere Team.')).toBeInTheDocument();
    });

    it('shows the waiting state and Retract once this team confirmed', async () => {
        const user = userEvent.setup();
        const { panel } = await renderPanel(
            seatedTable({ confirmation: { status: 'CONFIRMING', confirmedTeamIds: ['team-1'] } }),
            allGames(),
        );
        mockedRetract.mockResolvedValue({ status: 'PLAYING', confirmedTeamIds: [] });

        expect(within(panel).getByText('Status: Bestätigung läuft · 1 von 2 bestätigt')).toBeInTheDocument();
        expect(within(panel).getByText('Bestätigt von: Team Alpha')).toBeInTheDocument();
        expect(within(panel).getByText('Warten auf das andere Team.')).toBeInTheDocument();
        expect(within(panel).queryByRole('button', { name: 'Alle Spiele bestätigen' })).not.toBeInTheDocument();

        // Retract needs no dialog.
        await user.click(within(panel).getByRole('button', { name: 'Bestätigung zurückziehen' }));
        await waitFor(() => expect(mockedRetract).toHaveBeenCalledTimes(1));
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });

    it('shows the frozen notice and keeps Confirm available when the other team confirmed', async () => {
        const { panel } = await renderPanel(
            seatedTable({ confirmation: { status: 'CONFIRMING', confirmedTeamIds: ['team-2'] } }),
            allGames(),
        );

        expect(within(panel).getByText('Bestätigt von: Team Beta')).toBeInTheDocument();
        expect(
            within(panel).getByText('Die Spiele sind eingefroren. Prüfe sie und bestätige ebenfalls.'),
        ).toBeInTheDocument();
        expect(within(panel).getByRole('button', { name: 'Alle Spiele bestätigen' })).toBeEnabled();
        expect(within(panel).queryByRole('button', { name: 'Bestätigung zurückziehen' })).not.toBeInTheDocument();
    });

    it('is final once completed: no controls', async () => {
        const { panel } = await renderPanel(
            seatedTable({ confirmation: { status: 'COMPLETED', confirmedTeamIds: ['team-1', 'team-2'] } }),
            allGames(),
        );

        expect(within(panel).getByText('Status: Abgeschlossen · 2 von 2 bestätigt')).toBeInTheDocument();
        expect(within(panel).getByText('Die Spiele sind endgültig.')).toBeInTheDocument();
        expect(within(panel).queryByRole('button')).not.toBeInTheDocument();
    });

    it('counts confirmations out of three at a trio table and marks the games the team sits out', async () => {
        const trioGames = trioTable().schedule.map((s) => game(s.gameNumber, 1, s.teamIds));
        const { panel } = await renderPanel(trioTable(['team-2']), trioGames);

        expect(within(panel).getByText('Status: Bestätigung läuft · 1 von 3 bestätigt')).toBeInTheDocument();
        // Every team sits out two games; the viewer (Team Alpha) games 3 and 6.
        const games = within(panel).getByRole('table', { name: 'Spiele' });
        expect(columnHeaders(games)).toEqual(['Spiel', 'Team Alpha', 'Team Beta', 'Team Gamma', 'Hinweise']);
        expect(bodyRows(games).map((row) => row.slice(0, 4))).toEqual([
            ['1', '60', '40', 'Pause'],
            ['2', '60', 'Pause', '40'],
            ['3', 'Pause', '60', '40'],
            ['4', '60', '40', 'Pause'],
            ['5', '60', 'Pause', '40'],
            ['6', 'Pause', '60', '40'],
        ]);
    });

    it('explains a stale confirm and refetches the games', async () => {
        const user = userEvent.setup();
        const { panel } = await renderPanel(seatedTable(), allGames());
        mockedConfirm.mockRejectedValue(
            new ApiError({ code: 'GAME_CONCURRENT_MODIFICATION', status: 409, field: null, message: 'stale' }),
        );
        const fetchesBefore = mockedGames.mock.calls.length;

        await user.click(within(panel).getByRole('button', { name: 'Alle Spiele bestätigen' }));
        await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Bestätigen' }));

        expect(
            await within(panel).findByText(
                'Die Spiele haben sich geändert, seit du sie angesehen hast. Prüfe sie und bestätige erneut.',
            ),
        ).toBeInTheDocument();
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
        await waitFor(() => expect(mockedGames.mock.calls.length).toBeGreaterThan(fetchesBefore));
    });

    it('hides the panel but keeps the scoring link when the team is not seated yet', async () => {
        mockedMe.mockResolvedValue(overview);
        mockedCurrentTable.mockRejectedValue(
            new ApiError({ code: 'NOT_FOUND', status: 404, field: null, message: 'not seated' }),
        );

        renderApp('/team');

        expect(await screen.findByRole('link', { name: 'Ergebnisse eintragen' })).toBeInTheDocument();
        expect(screen.queryByRole('region', { name: /Runde/ })).not.toBeInTheDocument();
    });

    describe('polling', () => {
        beforeEach(() => vi.useFakeTimers({ shouldAdvanceTime: true }));
        afterEach(() => vi.useRealTimers());

        it('refetches every 30 s while the table is open and stops once it is completed', async () => {
            await renderPanel(seatedTable(), allGames());
            const tableFetches = mockedCurrentTable.mock.calls.length;
            const gameFetches = mockedGames.mock.calls.length;

            // Open: both the table state and the games poll.
            await act(() => vi.advanceTimersByTimeAsync(31_000));
            expect(mockedCurrentTable.mock.calls.length).toBe(tableFetches + 1);
            expect(mockedGames.mock.calls.length).toBe(gameFetches + 1);

            // Completed: the next poll brings the final state, then polling stops.
            mockedCurrentTable.mockResolvedValue(
                seatedTable({ confirmation: { status: 'COMPLETED', confirmedTeamIds: ['team-1', 'team-2'] } }),
            );
            await act(() => vi.advanceTimersByTimeAsync(30_000));
            await screen.findByText('Die Spiele sind endgültig.');
            const finalTableFetches = mockedCurrentTable.mock.calls.length;
            const finalGameFetches = mockedGames.mock.calls.length;

            await act(() => vi.advanceTimersByTimeAsync(120_000));
            expect(mockedCurrentTable.mock.calls.length).toBe(finalTableFetches);
            expect(mockedGames.mock.calls.length).toBe(finalGameFetches);
        });
    });
});
