import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { RecordedGame, TeamTable } from '@/domain/game.domain.ts';
import { ApiError } from '@/shared/api/api-error.ts';
import { ScoringRepository } from '@/scoring-api/scoring.repository.ts';
import { TeamAuthRepository } from '@/team-auth-api/team-auth.repository.ts';
import { TournamentStatus } from '@/vocabulary/tournament-status.enum.ts';
import { renderApp } from '@/test-helpers/render-app.tsx';

vi.mock('@/scoring-api/scoring.repository.ts', () => ({
    ScoringRepository: {
        currentTable: vi.fn(),
        games: vi.fn(),
        create: vi.fn(),
        update: vi.fn(),
    },
}));

// The /team/score route nests under /team, whose loader resolves GET /api/team/me;
// mock it so the parent guard passes and the scoring screen renders.
vi.mock('@/team-auth-api/team-auth.repository.ts', () => ({
    TeamAuthRepository: {
        exchange: vi.fn(),
        me: vi.fn(),
    },
}));

const scoring = vi.mocked(ScoringRepository);
const teamAuth = vi.mocked(TeamAuthRepository);

const teamOverview = {
    teamId: 'team-a',
    tournamentId: 't-1',
    tournamentName: 'Spring Classic',
    tournamentStatus: TournamentStatus.ACTIVE,
    teamName: 'Team Alpha',
    players: [
        { id: 'a1', name: 'Alice' },
        { id: 'a2', name: 'Anna' },
    ],
};

function table(overrides: Partial<TeamTable> = {}): TeamTable {
    return {
        tournamentId: 't-1',
        roundNumber: 1,
        tableNumber: 1,
        confirmation: { status: 'PLAYING', confirmedTeamIds: [] },
        grandTichuBonus: 200,
        doubleWinBonus: 200,
        teams: [
            {
                teamId: 'team-a',
                teamName: 'Team Alpha',
                players: [
                    { playerId: 'a1', name: 'Alice' },
                    { playerId: 'a2', name: 'Anna' },
                ],
            },
            {
                teamId: 'team-b',
                teamName: 'Team Beta',
                players: [
                    { playerId: 'b1', name: 'Bob' },
                    { playerId: 'b2', name: 'Ben' },
                ],
            },
        ],
        trio: false,
        schedule: [1, 2, 3, 4].map((gameNumber) => ({ gameNumber, teamIds: ['team-a', 'team-b'] })),
        ...overrides,
    };
}

function recordedGame(overrides: Partial<RecordedGame> = {}): RecordedGame {
    return {
        gameNumber: 1,
        version: 2,
        beschiss: false,
        teams: [
            {
                teamId: 'team-a',
                baseScore: 60,
                players: [
                    { playerId: 'a1', position: 1, call: 'SMALL', bombs: 0, tichuSuccess: true },
                    { playerId: 'a2', position: 3, call: 'NONE', bombs: 0, tichuSuccess: false },
                ],
            },
            {
                teamId: 'team-b',
                baseScore: 40,
                players: [
                    { playerId: 'b1', position: 2, call: 'NONE', bombs: 0, tichuSuccess: false },
                    { playerId: 'b2', position: 4, call: 'NONE', bombs: 0, tichuSuccess: false },
                ],
            },
        ],
        outcome: {
            tichuSuccesses: ['a1'],
            doubleWinTeam: null,
            teamPoints: { 'team-a': 160, 'team-b': 40 },
            victoryPoints: { 'team-a': 2, 'team-b': 0 },
        },
        ...overrides,
    };
}

describe('/team/score', () => {
    beforeEach(() => {
        scoring.currentTable.mockReset();
        scoring.games.mockReset();
        scoring.create.mockReset();
        scoring.update.mockReset();
        teamAuth.me.mockReset();
        teamAuth.me.mockResolvedValue(teamOverview);
    });

    it('redirects to /team/login when the current-table load answers 403 (admin cookie, no team session)', async () => {
        scoring.currentTable.mockRejectedValue(
            new ApiError({
                code: 'FORBIDDEN',
                status: 403,
                field: null,
                message: 'access denied',
            }),
        );

        const { router } = renderApp('/team/score');

        await waitFor(() =>
            expect(router.state.location.pathname).toBe('/team/login'),
        );
    });

    it('links back to the team overview', async () => {
        scoring.currentTable.mockResolvedValue(table());
        scoring.games.mockResolvedValue([]);

        renderApp('/team/score');

        expect(await screen.findByRole('link', { name: 'Zur Teamübersicht' })).toHaveAttribute('href', '/team');
    });

    it('does not prompt to confirm while a game is still missing', async () => {
        scoring.currentTable.mockResolvedValue(table());
        scoring.games.mockResolvedValue([1, 2, 3].map((gameNumber) => recordedGame({ gameNumber })));

        renderApp('/team/score');
        await screen.findByText('Team Alpha gegen Team Beta');

        expect(screen.queryByText('Alle Spiele sind eingetragen.')).not.toBeInTheDocument();
    });

    it('points to the overview to confirm once every game is entered', async () => {
        scoring.currentTable.mockResolvedValue(table());
        scoring.games.mockResolvedValue([1, 2, 3, 4].map((gameNumber) => recordedGame({ gameNumber })));

        renderApp('/team/score');

        const prompt = (await screen.findByText('Alle Spiele sind eingetragen.')).parentElement!;
        expect(within(prompt).getByRole('link', { name: 'Zur Bestätigung' })).toHaveAttribute('href', '/team');
    });

    it('renders the matchup, roster and a game selector', async () => {
        scoring.currentTable.mockResolvedValue(table());
        scoring.games.mockResolvedValue([]);

        renderApp('/team/score');

        expect(await screen.findByText('Team Alpha gegen Team Beta')).toBeInTheDocument();
        expect(screen.getByText('Alice')).toBeInTheDocument();
        expect(screen.getByText('Ben')).toBeInTheDocument();
        expect(screen.getAllByRole('tab')).toHaveLength(4);
    });

    it('submits raw facts for a normal game and records a create', async () => {
        scoring.currentTable.mockResolvedValue(table());
        scoring.games.mockResolvedValue([]);
        scoring.create.mockResolvedValue(recordedGame());
        const user = userEvent.setup();

        renderApp('/team/score');
        await screen.findByText('Team Alpha gegen Team Beta');

        // Finish buttons are in slot order: Alice(0), Anna(1), Bob(2), Ben(3).
        // Click Alice, Bob, Anna, Ben → positions [1,3,2,4] by slot: a valid split
        // game (1 and 2 on different teams, not a double win).
        const finishButtons = screen.getAllByRole('button', { name: /Nächste Platzierung setzen/ });
        await user.click(finishButtons[0]!); // Alice → 1
        await user.click(finishButtons[2]!); // Bob → 2
        await user.click(finishButtons[1]!); // Anna → 3
        await user.click(finishButtons[3]!); // Ben → 4

        await user.click(screen.getByRole('button', { name: 'Spiel speichern' }));

        await waitFor(() => expect(scoring.create).toHaveBeenCalledTimes(1));
        const [tableArg, input] = scoring.create.mock.calls[0]!;
        expect(tableArg.roundNumber).toBe(1);
        expect(tableArg.tableNumber).toBe(1);
        expect(input.version).toBeNull();
        expect(input.teams[0].players[0]).toEqual({
            playerId: 'a1',
            position: 1,
            call: 'NONE',
            bombs: 0,
        });
    });

    it('confirms a saved game in a status line and clears it on game switch', async () => {
        scoring.currentTable.mockResolvedValue(table());
        scoring.games.mockResolvedValue([]);
        scoring.create.mockResolvedValue(recordedGame());
        const user = userEvent.setup();

        renderApp('/team/score');
        await screen.findByText('Team Alpha gegen Team Beta');
        expect(screen.queryByText(/gespeichert\./)).not.toBeInTheDocument();

        const finishButtons = screen.getAllByRole('button', { name: /Nächste Platzierung setzen/ });
        await user.click(finishButtons[0]!);
        await user.click(finishButtons[2]!);
        await user.click(finishButtons[1]!);
        await user.click(finishButtons[3]!);
        await user.click(screen.getByRole('button', { name: 'Spiel speichern' }));

        // The message sits in a polite live region, so screen readers announce it.
        expect(await screen.findByText('Spiel 1 gespeichert.')).toHaveAttribute('role', 'status');

        await user.click(screen.getByRole('tab', { name: 'Spiel 2' }));
        expect(screen.queryByText('Spiel 1 gespeichert.')).not.toBeInTheDocument();
    });

    it('confirms an updated game', async () => {
        scoring.currentTable.mockResolvedValue(table());
        scoring.games.mockResolvedValue([recordedGame()]);
        scoring.update.mockResolvedValue({ ...recordedGame(), version: 2 });
        const user = userEvent.setup();

        renderApp('/team/score');
        await screen.findByText('Team Alpha gegen Team Beta');
        // The page opens on the first unentered game; pick the entered game 1.
        await user.click(screen.getAllByRole('tab')[0]!);

        await user.click(await screen.findByRole('button', { name: 'Spiel aktualisieren' }));

        expect(await screen.findByText('Spiel 1 aktualisiert.')).toHaveAttribute('role', 'status');
    });

    it('disables the base-score slider on a double win (positions 1+2 same team)', async () => {
        scoring.currentTable.mockResolvedValue(table());
        scoring.games.mockResolvedValue([]);
        const user = userEvent.setup();

        renderApp('/team/score');
        await screen.findByText('Team Alpha gegen Team Beta');

        const slider = screen.getByRole('slider');
        expect(slider).toBeEnabled();

        // Alice (1) then Anna (2): both on team 0 → double win.
        const finishButtons = screen.getAllByRole('button', { name: /Nächste Platzierung setzen/ });
        await user.click(finishButtons[0]!);
        await user.click(finishButtons[1]!);

        await waitFor(() => expect(screen.getByRole('slider')).toBeDisabled());
    });

    it('steps the base score by 5 with the plus/minus buttons and clamps at the range ends', async () => {
        scoring.currentTable.mockResolvedValue(table());
        scoring.games.mockResolvedValue([]);
        const user = userEvent.setup();

        renderApp('/team/score');
        await screen.findByText('Team Alpha gegen Team Beta');

        const slider = screen.getByRole('slider');
        expect(slider).toHaveValue('50');

        await user.click(screen.getByRole('button', { name: '5 Punkte mehr' }));
        expect(slider).toHaveValue('55');

        const less = screen.getByRole('button', { name: '5 Punkte weniger' });
        for (let i = 0; i < 20; i++) {
            if (less.hasAttribute('disabled')) break;
            await user.click(less);
        }
        expect(slider).toHaveValue('-25');
        expect(less).toBeDisabled();
    });

    it('moves between games with the arrow keys (tabs pattern)', async () => {
        scoring.currentTable.mockResolvedValue(table());
        scoring.games.mockResolvedValue([]);
        const user = userEvent.setup();

        renderApp('/team/score');
        await screen.findByText('Team Alpha gegen Team Beta');

        const tabs = screen.getAllByRole('tab');
        expect(tabs[0]).toHaveAttribute('aria-selected', 'true');
        expect(tabs[1]).toHaveAttribute('tabindex', '-1');

        tabs[0]!.focus();
        await user.keyboard('{ArrowRight}');
        expect(screen.getByRole('tab', { name: 'Spiel 2' })).toHaveAttribute('aria-selected', 'true');
        expect(screen.getByRole('tab', { name: 'Spiel 2' })).toHaveFocus();

        await user.keyboard('{ArrowLeft}{ArrowLeft}');
        expect(screen.getByRole('tab', { name: 'Spiel 4' })).toHaveAttribute('aria-selected', 'true');
        expect(screen.getByRole('tabpanel')).toHaveAccessibleName('Spiel 4');
    });

    it('offers grand Tichu at a bonus of 100 and totals it like a small Tichu', async () => {
        scoring.currentTable.mockResolvedValue(table({ grandTichuBonus: 100 }));
        scoring.games.mockResolvedValue([]);
        scoring.create.mockResolvedValue(recordedGame());
        const user = userEvent.setup();

        renderApp('/team/score');
        await screen.findByText('Team Alpha gegen Team Beta');

        await user.click(screen.getByRole('button', { name: 'Tichu-Ansage – Alice: keine' }));
        await user.click(screen.getByRole('button', { name: 'Tichu-Ansage – Alice: Tichu' }));
        expect(screen.getByRole('button', { name: 'Tichu-Ansage – Alice: Großes' })).toBeInTheDocument();

        const finishButtons = screen.getAllByRole('button', { name: /Nächste Platzierung setzen/ });
        await user.click(finishButtons[0]!); // Alice → 1
        await user.click(finishButtons[2]!); // Bob → 2
        await user.click(finishButtons[1]!); // Anna → 3
        await user.click(finishButtons[3]!); // Ben → 4

        // Alice's grand Tichu succeeded: base 50 + 100.
        expect(screen.getByText('→ 150')).toBeInTheDocument();

        await user.click(screen.getByRole('button', { name: 'Spiel speichern' }));
        await waitFor(() => expect(scoring.create).toHaveBeenCalledTimes(1));
        expect(scoring.create.mock.calls[0]![1].teams[0].players[0].call).toBe('GRAND');
    });

    it('totals a double win with the tournament bonus, here 100', async () => {
        scoring.currentTable.mockResolvedValue(table({ doubleWinBonus: 100 }));
        scoring.games.mockResolvedValue([]);
        const user = userEvent.setup();

        renderApp('/team/score');
        await screen.findByText('Team Alpha gegen Team Beta');

        const finishButtons = screen.getAllByRole('button', { name: /Nächste Platzierung setzen/ });
        await user.click(finishButtons[0]!); // Alice → 1
        await user.click(finishButtons[1]!); // Anna → 2

        // Team Alpha's double win: base 0 + 100.
        expect(screen.getByText('→ 100')).toBeInTheDocument();
    });

    it('announces each player\'s current call and bomb count, not just the control name', async () => {
        scoring.currentTable.mockResolvedValue(table());
        scoring.games.mockResolvedValue([]);
        const user = userEvent.setup();

        renderApp('/team/score');
        await screen.findByText('Team Alpha gegen Team Beta');

        await user.click(screen.getByRole('button', { name: 'Tichu-Ansage – Alice: keine' }));
        expect(screen.getByRole('button', { name: 'Tichu-Ansage – Alice: Tichu' })).toBeInTheDocument();

        await user.click(screen.getByRole('button', { name: 'Bomben – Alice: 0' }));
        expect(screen.getByRole('button', { name: 'Bomben – Alice: 1' })).toBeInTheDocument();
    });

    describe('on a frozen table', () => {
        it('names who confirmed and links to the overview while another team confirmed', async () => {
            scoring.currentTable.mockResolvedValue(
                table({ confirmation: { status: 'CONFIRMING', confirmedTeamIds: ['team-b'] } }),
            );
            scoring.games.mockResolvedValue([recordedGame()]);

            renderApp('/team/score');

            expect(
                await screen.findByText(
                    'Bestätigt von: Team Beta. Um ein Spiel zu ändern, muss das bestätigende Team in seiner Teamübersicht zurückziehen.',
                ),
            ).toBeInTheDocument();
            // Frozen: the confirm prompt gives way to the notice.
            expect(screen.queryByText('Alle Spiele sind eingetragen.')).not.toBeInTheDocument();
            // Every interactive control is disabled while frozen.
            await userEvent.click(screen.getAllByRole('tab')[0]!);
            expect(screen.getByRole('slider')).toBeDisabled();
            expect(screen.getByRole('button', { name: 'Spiel aktualisieren' })).toBeDisabled();
        });

        it('addresses the viewer when its own team confirmed', async () => {
            scoring.currentTable.mockResolvedValue(
                table({ confirmation: { status: 'CONFIRMING', confirmedTeamIds: ['team-a'] } }),
            );
            scoring.games.mockResolvedValue([recordedGame()]);

            renderApp('/team/score');

            expect(
                await screen.findByText(
                    'Dein Team hat diese Spiele bestätigt. Um eines zu ändern, ziehe die Bestätigung in der Teamübersicht zurück.',
                ),
            ).toBeInTheDocument();
            // The back link at the top is the way to the overview; the notice does not repeat it.
            expect(screen.getAllByRole('link', { name: 'Zur Teamübersicht' })).toHaveLength(1);
        });

        it('says the games are final once the table is completed', async () => {
            scoring.currentTable.mockResolvedValue(
                table({ confirmation: { status: 'COMPLETED', confirmedTeamIds: ['team-a', 'team-b'] } }),
            );
            scoring.games.mockResolvedValue([recordedGame()]);

            renderApp('/team/score');

            expect(
                await screen.findByText('Dieser Tisch ist abgeschlossen; seine Spiele sind endgültig.'),
            ).toBeInTheDocument();
            expect(screen.getByRole('slider')).toBeDisabled();
        });

        it('is read-only once the tournament has completed, even on an open table', async () => {
            teamAuth.me.mockResolvedValue({ ...teamOverview, tournamentStatus: TournamentStatus.COMPLETED });
            scoring.currentTable.mockResolvedValue(table());
            scoring.games.mockResolvedValue([recordedGame()]);

            renderApp('/team/score');
            await screen.findByText('Team Alpha gegen Team Beta');
            await userEvent.click(screen.getAllByRole('tab')[0]!);

            expect(
                await screen.findByText('Das Turnier ist beendet – die Spiele können nicht mehr geändert werden.'),
            ).toBeInTheDocument();
            expect(screen.getByRole('slider')).toBeDisabled();
            expect(screen.getByRole('button', { name: 'Spiel aktualisieren' })).toBeDisabled();
        });
    });

    it('marks an entered game in the selector and preloads it for editing', async () => {
        scoring.currentTable.mockResolvedValue(table());
        scoring.games.mockResolvedValue([recordedGame()]);

        renderApp('/team/score');
        await screen.findByText('Team Alpha gegen Team Beta');

        // Game 1 is entered → its tab carries the ✓ marker; the page opens on the
        // first unentered game (2), and selecting game 1 preloads it for editing.
        const firstTab = screen.getAllByRole('tab')[0]!;
        expect(within(firstTab).getByText(/✓/)).toBeInTheDocument();
        expect(firstTab).toHaveAccessibleName('Spiel 1 eingetragen');
        expect(screen.getAllByRole('tab')[1]).toHaveAttribute('aria-selected', 'true');
        await userEvent.click(firstTab);
        expect(screen.getByRole('button', { name: 'Spiel aktualisieren' })).toBeInTheDocument();
    });

    it('surfaces a localized validation error from the server', async () => {
        scoring.currentTable.mockResolvedValue(table());
        scoring.games.mockResolvedValue([]);
        scoring.create.mockRejectedValue(
            new ApiError({
                code: 'GAME_VALIDATION_INVALID',
                status: 400,
                field: 'baseScore',
                message: 'invalid',
            }),
        );
        const user = userEvent.setup();

        renderApp('/team/score');
        await screen.findByText('Team Alpha gegen Team Beta');

        const finishButtons = screen.getAllByRole('button', { name: /Nächste Platzierung setzen/ });
        await user.click(finishButtons[0]!); // Alice → 1
        await user.click(finishButtons[2]!); // Bob → 2
        await user.click(finishButtons[1]!); // Anna → 3
        await user.click(finishButtons[3]!); // Ben → 4
        await user.click(screen.getByRole('button', { name: 'Spiel speichern' }));

        expect(
            await screen.findByText('Das Spiel konnte nicht gespeichert werden – bitte die Eingaben prüfen und erneut versuchen.'),
        ).toBeInTheDocument();
    });

    it('blocks submit with a client-side message when positions are incomplete', async () => {
        scoring.currentTable.mockResolvedValue(table());
        scoring.games.mockResolvedValue([]);
        const user = userEvent.setup();

        renderApp('/team/score');
        await screen.findByText('Team Alpha gegen Team Beta');

        // Only one finish assigned → the zod resolver rejects before any request.
        const finishButtons = screen.getAllByRole('button', { name: /Nächste Platzierung setzen/ });
        await user.click(finishButtons[0]!);
        await user.click(screen.getByRole('button', { name: 'Spiel speichern' }));

        expect(
            await screen.findByText(
                'Setze die Platzierung jedes Spielers (1–4, oder 1 und 2 bei einem Doppelsieg).',
            ),
        ).toBeInTheDocument();
        expect(scoring.create).not.toHaveBeenCalled();
    });

    it('flips to read-only when the server reports GAME_CONFIRMED mid-edit', async () => {
        scoring.currentTable.mockResolvedValue(table());
        scoring.games.mockResolvedValue([]);
        scoring.create.mockRejectedValue(
            new ApiError({ code: 'GAME_CONFIRMED', status: 409, field: null, message: 'locked' }),
        );
        const user = userEvent.setup();

        renderApp('/team/score');
        await screen.findByText('Team Alpha gegen Team Beta');

        const finishButtons = screen.getAllByRole('button', { name: /Nächste Platzierung setzen/ });
        await user.click(finishButtons[0]!);
        await user.click(finishButtons[2]!);
        await user.click(finishButtons[1]!);
        await user.click(finishButtons[3]!);
        await user.click(screen.getByRole('button', { name: 'Spiel speichern' }));

        expect(
            await screen.findByText('Dieser Tisch ist bestätigt; seine Spiele können nicht mehr geändert werden.'),
        ).toBeInTheDocument();
        // The table state reloads, so the frozen explanation can name who confirmed.
        await waitFor(() => expect(scoring.currentTable.mock.calls.length).toBeGreaterThan(1));
        // The form latched read-only: the slider and save button are now disabled.
        await waitFor(() => expect(screen.getByRole('slider')).toBeDisabled());
        expect(screen.getByRole('button', { name: 'Spiel speichern' })).toBeDisabled();
    });

    it('offers a reload prompt on GAME_CONCURRENT_MODIFICATION', async () => {
        scoring.currentTable.mockResolvedValue(table());
        scoring.games.mockResolvedValue([recordedGame()]);
        scoring.update.mockRejectedValue(
            new ApiError({
                code: 'GAME_CONCURRENT_MODIFICATION',
                status: 409,
                field: null,
                message: 'stale',
            }),
        );
        const user = userEvent.setup();

        renderApp('/team/score');
        await screen.findByText('Team Alpha gegen Team Beta');

        // Game 1 is already entered → edit mode; submitting triggers a PUT that loses the race.
        await user.click(screen.getAllByRole('tab')[0]!);
        await user.click(screen.getByRole('button', { name: 'Spiel aktualisieren' }));

        expect(
            await screen.findByText(/Dieses Spiel wurde zwischenzeitlich geändert/),
        ).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Spiel neu laden' })).toBeInTheDocument();
    });

    describe('while polling', () => {
        beforeEach(() => vi.useFakeTimers({ shouldAdvanceTime: true }));
        afterEach(() => vi.useRealTimers());

        const theirEdit = () =>
            recordedGame({
                version: 3,
                teams: [
                    { ...recordedGame().teams[0], baseScore: 80 },
                    { ...recordedGame().teams[1], baseScore: 20 },
                ],
            });

        async function openGameOne() {
            const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
            scoring.currentTable.mockResolvedValue(table());
            scoring.games.mockResolvedValue([recordedGame()]);
            renderApp('/team/score');
            await screen.findByText('Team Alpha gegen Team Beta');
            await user.click(screen.getAllByRole('tab')[0]!);
            return user;
        }

        it('quietly loads the other team\'s newer version into an untouched form', async () => {
            await openGameOne();
            expect(screen.getByRole('slider')).toHaveValue('60');

            scoring.games.mockResolvedValue([theirEdit()]);
            await act(() => vi.advanceTimersByTimeAsync(31_000));

            await waitFor(() => expect(screen.getByRole('slider')).toHaveValue('80'));
            expect(screen.queryByText('Das andere Team hat dieses Spiel geändert.')).not.toBeInTheDocument();
        });

        it('keeps unsaved input and offers their version when the game changed elsewhere', async () => {
            const user = await openGameOne();
            await user.click(screen.getByRole('button', { name: 'Bomben – Alice: 0' }));

            scoring.games.mockResolvedValue([theirEdit()]);
            await act(() => vi.advanceTimersByTimeAsync(31_000));

            expect(await screen.findByText('Das andere Team hat dieses Spiel geändert.')).toBeInTheDocument();
            expect(screen.getByRole('button', { name: 'Bomben – Alice: 1' })).toBeInTheDocument();
            expect(screen.getByRole('slider')).toHaveValue('60');

            await user.click(screen.getByRole('button', { name: 'Deren Version laden' }));
            expect(screen.getByRole('slider')).toHaveValue('80');
            expect(screen.getByRole('button', { name: 'Bomben – Alice: 0' })).toBeInTheDocument();
            expect(screen.queryByText('Das andere Team hat dieses Spiel geändert.')).not.toBeInTheDocument();
        });

        it('saving over a change from elsewhere still sends the version the form was loaded with', async () => {
            const user = await openGameOne();
            await user.click(screen.getByRole('button', { name: 'Bomben – Alice: 0' }));
            scoring.games.mockResolvedValue([theirEdit()]);
            await act(() => vi.advanceTimersByTimeAsync(31_000));
            await screen.findByText('Das andere Team hat dieses Spiel geändert.');
            scoring.update.mockRejectedValue(
                new ApiError({ code: 'GAME_CONCURRENT_MODIFICATION', status: 409, field: null, message: 'stale' }),
            );

            await user.click(screen.getByRole('button', { name: 'Spiel aktualisieren' }));

            await waitFor(() => expect(scoring.update).toHaveBeenCalledTimes(1));
            expect(scoring.update.mock.calls[0]![1]).toMatchObject({ gameNumber: 1, version: 2 });
        });

        it('keeps a new entry when the other team creates the same game meanwhile', async () => {
            const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
            scoring.currentTable.mockResolvedValue(table());
            scoring.games.mockResolvedValue([]);
            renderApp('/team/score');
            await screen.findByText('Team Alpha gegen Team Beta');
            await user.click(screen.getByRole('button', { name: 'Bomben – Alice: 0' }));

            scoring.games.mockResolvedValue([recordedGame()]);
            await act(() => vi.advanceTimersByTimeAsync(31_000));

            expect(await screen.findByText('Das andere Team hat dieses Spiel geändert.')).toBeInTheDocument();
            expect(screen.getByRole('button', { name: 'Bomben – Alice: 1' })).toBeInTheDocument();
        });
    });

    it('does not mistake its own save for a change from elsewhere while the games catch up', async () => {
        scoring.currentTable.mockResolvedValue(table());
        // The games read still returns version 2 after the save answered version 3.
        scoring.games.mockResolvedValue([recordedGame()]);
        scoring.update.mockResolvedValue(recordedGame({ version: 3 }));
        const user = userEvent.setup();

        renderApp('/team/score');
        await screen.findByText('Team Alpha gegen Team Beta');
        await user.click(screen.getAllByRole('tab')[0]!);
        await user.click(screen.getByRole('button', { name: 'Spiel aktualisieren' }));

        expect(await screen.findByText('Spiel 1 aktualisiert.')).toBeInTheDocument();
        await waitFor(() => expect(scoring.games.mock.calls.length).toBeGreaterThan(1));
        expect(screen.queryByText('Das andere Team hat dieses Spiel geändert.')).not.toBeInTheDocument();
    });

    it('opens on game 1 when every game is entered', async () => {
        scoring.currentTable.mockResolvedValue(table());
        scoring.games.mockResolvedValue([1, 2, 3, 4].map((gameNumber) => recordedGame({ gameNumber })));

        renderApp('/team/score');
        await screen.findByText('Team Alpha gegen Team Beta');

        expect(screen.getAllByRole('tab')[0]).toHaveAttribute('aria-selected', 'true');
    });

    it('shows no per-game matchup line at a normal table', async () => {
        scoring.currentTable.mockResolvedValue(table());
        scoring.games.mockResolvedValue([]);

        renderApp('/team/score');
        await screen.findByText('Team Alpha gegen Team Beta');

        expect(screen.queryByText(/^Spiel 1:/)).not.toBeInTheDocument();
        expect(screen.queryByText(/setzt.*aus/)).not.toBeInTheDocument();
    });

    describe('at a trio table', () => {
        // Seats A/B/C = Alpha/Beta/Gamma; the viewing team is Alpha (team-a), which
        // sits out games 3 and 6 (BC).
        function trioTable(): TeamTable {
            const base = table();
            return {
                ...base,
                teams: [
                    base.teams[0],
                    base.teams[1],
                    {
                        teamId: 'team-c',
                        teamName: 'Team Gamma',
                        players: [
                            { playerId: 'c1', name: 'Cleo' },
                            { playerId: 'c2', name: 'Carl' },
                        ],
                    },
                ],
                trio: true,
                schedule: [
                    { gameNumber: 1, teamIds: ['team-a', 'team-b'] },
                    { gameNumber: 2, teamIds: ['team-a', 'team-c'] },
                    { gameNumber: 3, teamIds: ['team-b', 'team-c'] },
                    { gameNumber: 4, teamIds: ['team-a', 'team-b'] },
                    { gameNumber: 5, teamIds: ['team-a', 'team-c'] },
                    { gameNumber: 6, teamIds: ['team-b', 'team-c'] },
                ],
            };
        }

        it('lists all three teams and six games, opening on the first unentered one with its matchup', async () => {
            scoring.currentTable.mockResolvedValue(trioTable());
            scoring.games.mockResolvedValue([recordedGame({ gameNumber: 1 })]);

            renderApp('/team/score');

            expect(await screen.findByText('Trio-Tisch: Team Alpha, Team Beta, Team Gamma')).toBeInTheDocument();
            const tabs = screen.getAllByRole('tab');
            expect(tabs).toHaveLength(6);
            expect(tabs[1]).toHaveAttribute('aria-selected', 'true');
            expect(screen.getByText('Spiel 2: Team Alpha gegen Team Gamma')).toBeInTheDocument();
            // The form takes game 2's playing teams: Gamma's players replace Beta's.
            expect(screen.getByText('Cleo')).toBeInTheDocument();
            expect(screen.queryByText('Bob')).not.toBeInTheDocument();
        });

        it('marks the games the viewing team sits out, which stay enterable', async () => {
            scoring.currentTable.mockResolvedValue(trioTable());
            scoring.games.mockResolvedValue([]);

            renderApp('/team/score');
            await screen.findByText('Trio-Tisch: Team Alpha, Team Beta, Team Gamma');

            const tabs = screen.getAllByRole('tab');
            expect(tabs[2]).toHaveAccessibleName('Spiel 3 – Dein Team setzt aus');
            expect(tabs[5]).toHaveAccessibleName('Spiel 6 – Dein Team setzt aus');
            expect(tabs[1]).toHaveAccessibleName('Spiel 2');

            await userEvent.click(tabs[2]!);

            expect(screen.getByText('Spiel 3: Team Beta gegen Team Gamma')).toBeInTheDocument();
            expect(screen.getByText(/Dein Team setzt dieses Spiel aus/)).toBeInTheDocument();
            expect(screen.getByRole('button', { name: 'Spiel speichern' })).toBeEnabled();
        });

        it('submits the selected game\'s playing teams', async () => {
            scoring.currentTable.mockResolvedValue(trioTable());
            scoring.games.mockResolvedValue([]);
            scoring.create.mockResolvedValue(recordedGame({ gameNumber: 3 }));
            const user = userEvent.setup();

            renderApp('/team/score');
            await screen.findByText('Trio-Tisch: Team Alpha, Team Beta, Team Gamma');
            await user.click(screen.getAllByRole('tab')[2]!);

            // Slot order: Bob(0), Ben(1), Cleo(2), Carl(3).
            const finishButtons = screen.getAllByRole('button', { name: /Nächste Platzierung setzen/ });
            await user.click(finishButtons[0]!);
            await user.click(finishButtons[2]!);
            await user.click(finishButtons[1]!);
            await user.click(finishButtons[3]!);
            await user.click(screen.getByRole('button', { name: 'Spiel speichern' }));

            await waitFor(() => expect(scoring.create).toHaveBeenCalledTimes(1));
            const [, input] = scoring.create.mock.calls[0]!;
            expect(input.gameNumber).toBe(3);
            expect(input.teams.map((team) => team.teamId)).toEqual(['team-b', 'team-c']);
            expect(input.teams[1].players.map((player) => player.playerId)).toEqual(['c1', 'c2']);
        });
    });

    it('shows the not-seated placeholder on a 404', async () => {
        scoring.currentTable.mockRejectedValue(
            new ApiError({ code: 'NOT_FOUND', status: 404, field: null, message: 'not seated' }),
        );

        renderApp('/team/score');

        expect(
            await screen.findByText('Dein Tisch erscheint, sobald das Turnier gestartet ist.'),
        ).toBeInTheDocument();
        expect(screen.getByRole('link', { name: 'Zur Teamübersicht' })).toHaveAttribute('href', '/team');
    });
});
