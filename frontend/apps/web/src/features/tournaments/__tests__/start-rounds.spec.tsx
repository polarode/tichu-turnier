import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthRepository } from '@/auth-api/auth.repository.ts';
import type { RecordedGame, TableConfirmation } from '@/domain/game.domain.ts';
import type { Player } from '@/domain/player.domain.ts';
import type { Round, TableOverview } from '@/domain/round.domain.ts';
import type { Team } from '@/domain/team.domain.ts';
import type { Tournament } from '@/domain/tournament.domain.ts';
import { ApiError } from '@/shared/api/api-error.ts';
import { TeamRepository } from '@/team-api/team.repository.ts';
import { TournamentRepository } from '@/tournament-api/tournament.repository.ts';
import { renderApp } from '@/test-helpers/render-app.tsx';
import { TableStatus } from '@/vocabulary/table-status.enum.ts';

vi.mock('@/auth-api/auth.repository.ts', () => ({
    AuthRepository: { register: vi.fn(), login: vi.fn(), refresh: vi.fn(), logout: vi.fn() },
}));
vi.mock('@/tournament-api/tournament.repository.ts', () => ({
    TournamentRepository: {
        list: vi.fn(),
        get: vi.fn(),
        create: vi.fn(),
        update: vi.fn(),
        remove: vi.fn(),
        start: vi.fn(),
        rounds: vi.fn(),
    },
}));
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

function tournament(status: Tournament['status']): Tournament {
    return {
        id: 't-1',
        name: 'Jahresturnier',
        description: '',
        maxTeams: 16,
        settings: { allowGrandTichu: false },
        status,
        createdAt: new Date('2026-01-01T00:00:00Z'),
        updatedAt: new Date('2026-01-01T00:00:00Z'),
    };
}

function team(id: string, name: string): Team {
    const players: readonly [Player, Player] = [
        { id: `${id}-p1`, teamId: id, name: `${name} 1` },
        { id: `${id}-p2`, teamId: id, name: `${name} 2` },
    ];
    return { id, tournamentId: 't-1', name, players };
}

function round(number: number, tables: Round['tables']): Round {
    return { number, tables };
}

function table(
    tableNumber: number,
    homeTeamId: string,
    awayTeamId: string,
    games: RecordedGame[] = [],
    confirmation: TableConfirmation = { status: TableStatus.PLAYING, confirmedTeamIds: [] },
): TableOverview {
    const schedule = [1, 2, 3, 4].map((gameNumber) => ({
        gameNumber,
        teamIds: [homeTeamId, awayTeamId] as [string, string],
    }));
    return { tableNumber, teamIds: [homeTeamId, awayTeamId], trio: false, confirmation, gameCount: 4, schedule, games };
}

function allGames(): RecordedGame[] {
    return [1, 2, 3, 4].map((gameNumber) => game(gameNumber, { a: 100, b: 0 }));
}

/**
 * A recorded game between team-a and team-b. The teams are stored away-first
 * (team-b, then team-a) on purpose: the view must order scores home : away by
 * team id, not by stored order.
 */
function game(
    gameNumber: number,
    points: { a: number; b: number },
    options: { beschiss?: boolean; doubleWinTeam?: string } = {},
): RecordedGame {
    const player = (playerId: string) => ({
        playerId,
        position: null,
        call: 'NONE' as const,
        bombs: 0,
        tichuSuccess: false,
    });
    return {
        gameNumber,
        version: 1,
        beschiss: options.beschiss ?? false,
        teams: [
            { teamId: 'team-b', baseScore: 0, players: [player('b1'), player('b2')] },
            { teamId: 'team-a', baseScore: 0, players: [player('a1'), player('a2')] },
        ],
        outcome: {
            tichuSuccesses: [],
            doubleWinTeam: options.doubleWinTeam ?? null,
            teamPoints: { 'team-a': points.a, 'team-b': points.b },
            victoryPoints: {},
        },
    };
}

beforeEach(() => {
    vi.clearAllMocks();
    auth.refresh.mockResolvedValue(admin);
});

describe('admin start action', () => {
    it('starts the tournament after confirmation and shows the ACTIVE badge', async () => {
        tournaments.get.mockResolvedValue(tournament('SETUP'));
        teams.list.mockResolvedValue([team('team-a', 'Alpha'), team('team-b', 'Beta')]);
        tournaments.rounds.mockResolvedValue([]);
        tournaments.start.mockResolvedValue({
            tournament: tournament('ACTIVE'),
            round: round(1, [table(1, 'team-a', 'team-b')]),
        });
        renderApp('/admin/tournaments/t-1');

        await userEvent.click(await screen.findByRole('button', { name: 'Turnier starten' }));
        await userEvent.click(await screen.findByRole('button', { name: 'Bestätigen' }));

        expect(tournaments.start).toHaveBeenCalledWith('t-1');
        await waitFor(() =>
            expect(screen.getByText('Läuft')).toBeInTheDocument(),
        );
    });

    it('blocks the start button with a hint while there are fewer than two teams', async () => {
        tournaments.get.mockResolvedValue(tournament('SETUP'));
        teams.list.mockResolvedValue([team('team-a', 'Alpha')]);
        tournaments.rounds.mockResolvedValue([]);
        renderApp('/admin/tournaments/t-1');

        const button = await screen.findByRole('button', { name: 'Turnier starten' });
        expect(button).toBeDisabled();
        expect(screen.getByText(/mindestens zwei Teams/)).toBeInTheDocument();
        expect(tournaments.start).not.toHaveBeenCalled();
    });

    it('allows starting with an odd team count (trio table)', async () => {
        tournaments.get.mockResolvedValue(tournament('SETUP'));
        teams.list.mockResolvedValue([
            team('team-a', 'Alpha'),
            team('team-b', 'Beta'),
            team('team-c', 'Gamma'),
        ]);
        tournaments.rounds.mockResolvedValue([]);
        renderApp('/admin/tournaments/t-1');

        const button = await screen.findByRole('button', { name: 'Turnier starten' });
        expect(button).toBeEnabled();
        expect(screen.queryByText(/ungerade Teamanzahl/)).not.toBeInTheDocument();
    });

    it('shows the localized server error when starting is rejected', async () => {
        tournaments.get.mockResolvedValue(tournament('SETUP'));
        teams.list.mockResolvedValue([team('team-a', 'Alpha'), team('team-b', 'Beta')]);
        tournaments.rounds.mockResolvedValue([]);
        tournaments.start.mockRejectedValue(
            new ApiError({
                code: 'TOURNAMENT_NOT_STARTABLE',
                status: 409,
                field: null,
                message: 'not startable',
            }),
        );
        renderApp('/admin/tournaments/t-1');

        await userEvent.click(await screen.findByRole('button', { name: 'Turnier starten' }));
        await userEvent.click(await screen.findByRole('button', { name: 'Bestätigen' }));

        expect(
            await screen.findByText(
                'Dieses Turnier kann nicht gestartet werden, weil es nicht mehr in der Vorbereitung ist.',
            ),
        ).toBeInTheDocument();
    });
});

describe('rounds section', () => {
    it('renders tables with team names per round', async () => {
        tournaments.get.mockResolvedValue(tournament('ACTIVE'));
        teams.list.mockResolvedValue([team('team-a', 'Alpha'), team('team-b', 'Beta')]);
        tournaments.rounds.mockResolvedValue([
            round(1, [
                table(1, 'team-a', 'team-b'),
                table(2, 'team-b', 'team-a'),
            ]),
        ]);
        renderApp('/admin/tournaments/t-1');

        const section = await screen.findByRole('region', { name: 'Runden' });
        expect(await within(section).findByText('Runde 1')).toBeInTheDocument();
        expect(within(section).getByText('Tisch 1')).toBeInTheDocument();
        const tableRow = within(section).getByText((_, element) =>
            element?.textContent === 'Tisch 1: Alpha vs Beta',
        );
        expect(tableRow).toBeInTheDocument();
    });

    it('shows per-table game status and the entered scores home : away with markers', async () => {
        tournaments.get.mockResolvedValue(tournament('ACTIVE'));
        teams.list.mockResolvedValue([team('team-a', 'Alpha'), team('team-b', 'Beta')]);
        tournaments.rounds.mockResolvedValue([
            round(1, [
                table(1, 'team-a', 'team-b', [
                    game(1, { a: 155, b: 45 }, { beschiss: true }),
                    game(2, { a: 0, b: 300 }, { doubleWinTeam: 'team-b' }),
                ]),
            ]),
        ]);
        renderApp('/admin/tournaments/t-1');

        const section = await screen.findByRole('region', { name: 'Runden' });
        expect(await within(section).findByText('2/4 eingetragen')).toBeInTheDocument();

        // Games stay collapsed behind the table's summary line until it is opened.
        expect(within(section).queryAllByRole('listitem', { name: /^Spiel/ })).toHaveLength(0);
        const toggle = within(section).getByRole('button', { name: /Tisch 1.*Alpha vs Beta/ });
        expect(toggle).toHaveAttribute('aria-expanded', 'false');
        await userEvent.click(toggle);
        expect(toggle).toHaveAttribute('aria-expanded', 'true');

        // Every game of the table is listed; the two entered ones carry their scores.
        const rows = within(section).getAllByRole('listitem', { name: /^Spiel/ });
        expect(rows).toHaveLength(4);
        expect(within(rows[2]!).getByText('fehlt')).toBeInTheDocument();
        expect(within(section).queryByText('Trio')).not.toBeInTheDocument();
        const [first, second] = rows;
        expect(within(first!).getByText('155 : 45')).toBeInTheDocument();
        expect(within(first!).getByText('Beschiss')).toBeInTheDocument();
        expect(within(first!).queryByText('Doppelsieg')).not.toBeInTheDocument();
        expect(within(second!).getByText('0 : 300')).toBeInTheDocument();
        expect(within(second!).getByText('Doppelsieg')).toBeInTheDocument();
        expect(within(second!).queryByText('Beschiss')).not.toBeInTheDocument();
    });

    it("shows each table's status and marks the teams that confirmed", async () => {
        tournaments.get.mockResolvedValue(tournament('ACTIVE'));
        teams.list.mockResolvedValue([
            team('team-a', 'Alpha'),
            team('team-b', 'Beta'),
            team('team-c', 'Gamma'),
            team('team-d', 'Delta'),
            team('team-e', 'Epsilon'),
            team('team-f', 'Zeta'),
        ]);
        tournaments.rounds.mockResolvedValue([
            round(1, [
                table(1, 'team-a', 'team-b', allGames().slice(0, 3)),
                table(2, 'team-c', 'team-d', allGames(), { status: TableStatus.CONFIRMING, confirmedTeamIds: ['team-c'] }),
                table(3, 'team-e', 'team-f', allGames(), {
                    status: TableStatus.COMPLETED,
                    confirmedTeamIds: ['team-e', 'team-f'],
                }),
            ]),
        ]);
        renderApp('/admin/tournaments/t-1');

        const section = await screen.findByRole('region', { name: 'Runden' });
        const playing = await within(section).findByRole('button', { name: /^Tisch 1/ });
        const confirming = within(section).getByRole('button', { name: /^Tisch 2/ });
        const completed = within(section).getByRole('button', { name: /^Tisch 3/ });

        // Playing keeps the entry progress; once someone confirms, the chip shows the status.
        expect(within(playing).getByText('3/4 eingetragen')).toBeInTheDocument();
        expect(within(confirming).getByText('Bestätigung läuft · 1 von 2 bestätigt')).toBeInTheDocument();
        expect(within(confirming).queryByText(/eingetragen/)).not.toBeInTheDocument();
        expect(within(completed).getByText('Abgeschlossen')).toBeInTheDocument();

        // A ✓ follows each team that confirmed.
        expect(playing).toHaveTextContent('Tisch 1: Alpha vs Beta');
        expect(confirming).toHaveTextContent('Tisch 2: Gamma ✓ bestätigt vs Delta');
        expect(completed).toHaveTextContent('Tisch 3: Epsilon ✓ bestätigt vs Zeta ✓ bestätigt');
    });

    it('shows a trio table with its badge, three teams and six matchup rows', async () => {
        tournaments.get.mockResolvedValue(tournament('ACTIVE'));
        teams.list.mockResolvedValue([team('team-a', 'Alpha'), team('team-b', 'Beta'), team('team-c', 'Gamma')]);
        const trioGame: RecordedGame = {
            ...game(2, { a: 120, b: 0 }),
            // Stored C first; game 2 is A vs C by the schedule.
            teams: [
                { teamId: 'team-c', baseScore: 0, players: game(2, { a: 0, b: 0 }).teams[0].players },
                { teamId: 'team-a', baseScore: 0, players: game(2, { a: 0, b: 0 }).teams[1].players },
            ],
            outcome: { tichuSuccesses: [], doubleWinTeam: null, teamPoints: { 'team-a': 120, 'team-c': 80 }, victoryPoints: {} },
        };
        tournaments.rounds.mockResolvedValue([
            round(1, [
                {
                    tableNumber: 1,
                    teamIds: ['team-a', 'team-b', 'team-c'],
                    trio: true,
                    confirmation: { status: TableStatus.PLAYING, confirmedTeamIds: [] },
                    gameCount: 6,
                    schedule: [
                        { gameNumber: 1, teamIds: ['team-a', 'team-b'] },
                        { gameNumber: 2, teamIds: ['team-a', 'team-c'] },
                        { gameNumber: 3, teamIds: ['team-b', 'team-c'] },
                        { gameNumber: 4, teamIds: ['team-a', 'team-b'] },
                        { gameNumber: 5, teamIds: ['team-a', 'team-c'] },
                        { gameNumber: 6, teamIds: ['team-b', 'team-c'] },
                    ],
                    games: [trioGame],
                },
            ]),
        ]);
        renderApp('/admin/tournaments/t-1');

        const section = await screen.findByRole('region', { name: 'Runden' });
        expect(await within(section).findByText('1/6 eingetragen')).toBeInTheDocument();
        expect(within(section).getByText('Trio')).toBeInTheDocument();
        await userEvent.click(within(section).getByRole('button', { name: /Tisch 1.*Alpha vs Beta vs Gamma/ }));

        // All six games are listed with their matchup; only game 2 is entered.
        const rows = within(section).getAllByRole('listitem', { name: /^Spiel/ });
        expect(rows).toHaveLength(6);
        expect(rows.map((row) => within(row).getByText(/ vs /).textContent)).toEqual([
            'Alpha vs Beta',
            'Alpha vs Gamma',
            'Beta vs Gamma',
            'Alpha vs Beta',
            'Alpha vs Gamma',
            'Beta vs Gamma',
        ]);
        expect(within(rows[1]!).getByText('Alpha vs Gamma')).toBeInTheDocument();
        expect(within(rows[1]!).getByText('120 : 80')).toBeInTheDocument();
        expect(within(rows[2]!).getByText('Beta vs Gamma')).toBeInTheDocument();
        expect(within(rows[2]!).getByText('fehlt')).toBeInTheDocument();
        expect(within(rows[5]!).getByText('fehlt')).toBeInTheDocument();
    });

    it('opens on the latest round and switches rounds through the selector', async () => {
        tournaments.get.mockResolvedValue(tournament('ACTIVE'));
        teams.list.mockResolvedValue([team('team-a', 'Alpha'), team('team-b', 'Beta')]);
        const completedTable = table(1, 'team-a', 'team-b', allGames(), {
            status: TableStatus.COMPLETED,
            confirmedTeamIds: ['team-a', 'team-b'],
        });
        tournaments.rounds.mockResolvedValue([round(1, [completedTable]), round(2, [table(1, 'team-b', 'team-a')])]);
        renderApp('/admin/tournaments/t-1');

        const selector = await screen.findByRole('tablist', { name: 'Runde auswählen' });
        // Round 1 has every table completed, so it is marked complete.
        expect(within(selector).getByRole('tab', { name: 'Runde 1 vollständig' })).toBeInTheDocument();
        expect(within(selector).getByRole('tab', { name: 'Runde 2' })).toHaveAttribute('aria-selected', 'true');
        expect(screen.getByRole('heading', { name: 'Runde 2' })).toBeInTheDocument();
        expect(screen.getByText('0/4 eingetragen')).toBeInTheDocument();

        await userEvent.click(within(selector).getByRole('tab', { name: 'Runde 1 vollständig' }));
        expect(screen.getByRole('heading', { name: 'Runde 1' })).toBeInTheDocument();
        expect(screen.getByText('Abgeschlossen')).toBeInTheDocument();
    });

    it('marks a round complete only once every table is completed, not when all games are entered', async () => {
        tournaments.get.mockResolvedValue(tournament('ACTIVE'));
        teams.list.mockResolvedValue([team('team-a', 'Alpha'), team('team-b', 'Beta')]);
        const enteredTable = table(1, 'team-a', 'team-b', allGames());
        const confirmingTable = table(1, 'team-a', 'team-b', allGames(), {
            status: TableStatus.CONFIRMING,
            confirmedTeamIds: ['team-a'],
        });
        tournaments.rounds.mockResolvedValue([round(1, [enteredTable]), round(2, [confirmingTable])]);
        renderApp('/admin/tournaments/t-1');

        const selector = await screen.findByRole('tablist', { name: 'Runde auswählen' });
        expect(within(selector).getByRole('tab', { name: 'Runde 1' })).toBeInTheDocument();
        expect(within(selector).getByRole('tab', { name: 'Runde 2' })).toBeInTheDocument();
        expect(within(selector).queryByRole('tab', { name: /vollständig/ })).not.toBeInTheDocument();
    });

    it('splits a started tournament into Rounds and Teams tabs, Rounds first', async () => {
        tournaments.get.mockResolvedValue(tournament('ACTIVE'));
        teams.list.mockResolvedValue([team('team-a', 'Alpha'), team('team-b', 'Beta')]);
        tournaments.rounds.mockResolvedValue([round(1, [table(1, 'team-a', 'team-b')])]);
        const { router } = renderApp('/admin/tournaments/t-1');

        const sections = await screen.findByRole('tablist', { name: 'Turnierbereiche' });
        expect(await within(sections).findByRole('tab', { name: 'Runden (1)' })).toHaveAttribute(
            'aria-selected',
            'true',
        );
        expect(screen.queryByText('Alpha 1 & Alpha 2')).not.toBeInTheDocument();

        await userEvent.click(within(sections).getByRole('tab', { name: 'Teams (2)' }));
        expect(await screen.findByText('Alpha 1 & Alpha 2')).toBeInTheDocument();
        expect(screen.queryByRole('region', { name: 'Runden' })).not.toBeInTheDocument();
        await waitFor(() => expect(router.state.location.search).toEqual({ tab: 'teams' }));
    });

    it('restores the Teams tab from the URL', async () => {
        tournaments.get.mockResolvedValue(tournament('ACTIVE'));
        teams.list.mockResolvedValue([team('team-a', 'Alpha'), team('team-b', 'Beta')]);
        tournaments.rounds.mockResolvedValue([]);
        renderApp('/admin/tournaments/t-1?tab=teams');

        expect(await screen.findByText('Alpha 1 & Alpha 2')).toBeInTheDocument();
        expect(screen.getByRole('tab', { name: 'Teams (2)' })).toHaveAttribute('aria-selected', 'true');
    });

    it('shows no rounds section while the tournament is in setup', async () => {
        tournaments.get.mockResolvedValue(tournament('SETUP'));
        teams.list.mockResolvedValue([team('team-a', 'Alpha'), team('team-b', 'Beta')]);
        tournaments.rounds.mockResolvedValue([]);
        renderApp('/admin/tournaments/t-1');

        await screen.findByRole('button', { name: 'Turnier starten' });
        expect(screen.queryByRole('region', { name: 'Runden' })).not.toBeInTheDocument();
        // Setup shows the team list alone, without section tabs.
        expect(screen.queryByRole('tablist')).not.toBeInTheDocument();
        expect(screen.getByText('Alpha 1 & Alpha 2')).toBeInTheDocument();
    });
});
