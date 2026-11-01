import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

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
        locked: false,
        allowGrandTichu: true,
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

    it('loads an existing game read-only when the round is locked', async () => {
        scoring.currentTable.mockResolvedValue(table({ locked: true }));
        scoring.games.mockResolvedValue([recordedGame()]);

        renderApp('/team/score');

        expect(
            await screen.findByText('Diese Runde ist gesperrt. Ihre Spiele sind schreibgeschützt.'),
        ).toBeInTheDocument();
        // Every interactive control is disabled in a locked round.
        expect(screen.getByRole('slider')).toBeDisabled();
        expect(screen.getByRole('button', { name: 'Spiel aktualisieren' })).toBeDisabled();
    });

    it('marks an entered game in the selector and preloads it for editing', async () => {
        scoring.currentTable.mockResolvedValue(table());
        scoring.games.mockResolvedValue([recordedGame()]);

        renderApp('/team/score');
        await screen.findByText('Team Alpha gegen Team Beta');

        // Game 1 is entered → its tab carries the ✓ marker and the update label shows.
        const firstTab = screen.getAllByRole('tab')[0]!;
        expect(within(firstTab).getByText(/✓/)).toBeInTheDocument();
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
            await screen.findByText('Diese Runde ist gesperrt. Ihre Spiele sind schreibgeschützt.'),
        ).toBeInTheDocument();
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
        await user.click(screen.getByRole('button', { name: 'Spiel aktualisieren' }));

        expect(
            await screen.findByText(/Dieses Spiel wurde zwischenzeitlich geändert/),
        ).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Spiel neu laden' })).toBeInTheDocument();
    });

    it('shows the not-seated placeholder on a 404', async () => {
        scoring.currentTable.mockRejectedValue(
            new ApiError({ code: 'NOT_FOUND', status: 404, field: null, message: 'not seated' }),
        );

        renderApp('/team/score');

        expect(
            await screen.findByText('Dein Tisch erscheint, sobald das Turnier gestartet ist.'),
        ).toBeInTheDocument();
    });
});
