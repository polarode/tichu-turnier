import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthRepository } from '@/auth-api/auth.repository.ts';
import type { TableConfirmation } from '@/domain/game.domain.ts';
import type { Round, TableOverview } from '@/domain/round.domain.ts';
import type { Tournament } from '@/domain/tournament.domain.ts';
import { ApiError } from '@/shared/api/api-error.ts';
import { TeamRepository } from '@/team-api/team.repository.ts';
import { standingsDtoSchema } from '@/tournament-api/dtos/standings.dto.ts';
import { standingsEntryDtoToDomain } from '@/tournament-api/mappers/standings.mapper.ts';
import { TournamentRepository } from '@/tournament-api/tournament.repository.ts';
import { renderApp } from '@/test-helpers/render-app.tsx';
import { bodyRows, columnHeaders } from '@/test-helpers/table-rows.ts';
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
        standings: vi.fn(),
        setTotalRounds: vi.fn(),
        startNextRound: vi.fn(),
        finish: vi.fn(),
        publish: vi.fn(),
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

/**
 * The shared contract fixture: the backend's integration test asserts its API
 * answers exactly `response`; here the same wire data runs through the real zod
 * schema and mapper into the rendered table.
 */
const fixture = JSON.parse(
    readFileSync(
        resolve(__dirname, '../../../../../../../contract-fixtures/standings.json'),
        'utf8',
    ),
) as { response: unknown };

function tournament(status: Tournament['status'], totalRounds = 4): Tournament {
    return {
        id: 't-1',
        name: 'Jahresturnier',
        description: '',
        maxTeams: 16,
        settings: { grandTichuBonus: 200, doubleWinBonus: 200, totalRounds },
        status,
        createdAt: new Date('2026-01-01T00:00:00Z'),
        updatedAt: new Date('2026-01-01T00:00:00Z'),
    };
}

const completed: TableConfirmation = {
    status: TableStatus.COMPLETED,
    confirmedTeamIds: ['a', 'b'],
};
const playing: TableConfirmation = { status: TableStatus.PLAYING, confirmedTeamIds: [] };

function table(tableNumber: number, confirmation: TableConfirmation): TableOverview {
    const schedule = [1, 2, 3, 4].map((gameNumber) => ({
        gameNumber,
        teamIds: ['a', 'b'] as [string, string],
    }));
    return {
        tableNumber,
        teamIds: ['a', 'b'],
        trio: false,
        confirmation,
        gameCount: 4,
        schedule,
        games: [],
    };
}

/** [count] rounds; the last one has [completedTables] of [tableCount] tables completed, earlier ones are complete. */
function rounds(count: number, tableCount: number, completedTables: number): Round[] {
    return Array.from({ length: count }, (_, i) => ({
        number: i + 1,
        tables: Array.from({ length: tableCount }, (__, t) =>
            table(t + 1, i + 1 < count || t < completedTables ? completed : playing),
        ),
    }));
}

beforeEach(() => {
    vi.clearAllMocks();
    auth.refresh.mockResolvedValue({ id: 'admin-1', email: '[EMAIL]', role: 'ADMIN' });
    teams.list.mockResolvedValue([]);
    tournaments.standings.mockResolvedValue([]);
});

describe('standings tab', () => {
    it('sits between Rounds and Teams', async () => {
        tournaments.get.mockResolvedValue(tournament('ACTIVE'));
        tournaments.rounds.mockResolvedValue(rounds(1, 2, 0));
        renderApp('/admin/tournaments/t-1');

        const tabs = await screen.findByRole('tablist', { name: 'Turnierbereiche' });
        await waitFor(() =>
            expect(
                within(tabs)
                    .getAllByRole('tab')
                    .map((tab) => tab.textContent),
            ).toEqual(['Runden (1)', 'Rangliste', 'Teams (0)']),
        );
    });

    it('renders the shared contract fixture: ranks, shared ranks, games, VP and TP', async () => {
        tournaments.get.mockResolvedValue(tournament('ACTIVE'));
        tournaments.rounds.mockResolvedValue(rounds(2, 2, 0));
        tournaments.standings.mockImplementation(async () =>
            standingsDtoSchema.parse(fixture.response).map(standingsEntryDtoToDomain),
        );
        renderApp('/admin/tournaments/t-1?tab=standings');

        const standings = await screen.findByRole('table', { name: 'Rangliste' });
        expect(columnHeaders(standings)).toEqual(['Platz', 'Team', 'Spiele', 'SP', 'TP']);
        // Row header (team) first, then the cells: rank, games, VP, TP.
        expect(bodyRows(standings)).toEqual([
            ['Ravens', '1', '4', '8', '280'],
            ['Badgers', '2', '4', '4', '200'],
            ['Falcons', '2', '4', '4', '200'],
            ['Owls', '2', '4', '4', '200'],
            ['Wolves', '5', '4', '0', '120'],
        ]);
    });

    it('shows a note instead of a table of zeros until the first table is completed', async () => {
        tournaments.get.mockResolvedValue(tournament('ACTIVE'));
        tournaments.rounds.mockResolvedValue(rounds(1, 2, 0));
        tournaments.standings.mockResolvedValue([
            {
                rank: 1,
                teamId: 'a',
                teamName: 'Alpha',
                gamesPlayed: 0,
                victoryPoints: 0,
                tichuPoints: 0,
            },
            {
                rank: 1,
                teamId: 'b',
                teamName: 'Beta',
                gamesPlayed: 0,
                victoryPoints: 0,
                tichuPoints: 0,
            },
        ]);
        renderApp('/admin/tournaments/t-1?tab=standings');

        expect(
            await screen.findByText(
                'Die Rangliste erscheint, sobald der erste Tisch abgeschlossen ist.',
            ),
        ).toBeInTheDocument();
        expect(screen.queryByRole('table', { name: 'Rangliste' })).not.toBeInTheDocument();
    });
});

describe('progress card', () => {
    async function progressCard() {
        return screen.findByRole('region', { name: 'Fortschritt' });
    }

    it("shows the round of the total and the current round's completed tables", async () => {
        tournaments.get.mockResolvedValue(tournament('ACTIVE'));
        tournaments.rounds.mockResolvedValue(rounds(2, 5, 3));
        renderApp('/admin/tournaments/t-1');

        const card = await progressCard();
        await waitFor(() => expect(card).toHaveTextContent('Runde 2 von 4'));
        expect(card).toHaveTextContent('3/5 Tische abgeschlossen');
        expect(within(card).getByRole('progressbar')).toHaveAttribute('aria-valuenow', '3');
    });

    it('saves each step of the total rounds right away', async () => {
        tournaments.get.mockResolvedValue(tournament('ACTIVE'));
        tournaments.rounds.mockResolvedValue(rounds(2, 2, 0));
        tournaments.setTotalRounds.mockResolvedValue(tournament('ACTIVE', 5));
        renderApp('/admin/tournaments/t-1');

        const card = await progressCard();
        await userEvent.click(within(card).getByRole('button', { name: 'Runde hinzufügen' }));

        expect(tournaments.setTotalRounds).toHaveBeenCalledWith('t-1', 5);
        await waitFor(() => expect(card).toHaveTextContent('Runde 2 von 5'));
    });

    it('cannot step below the rounds already drawn', async () => {
        tournaments.get.mockResolvedValue(tournament('ACTIVE', 2));
        tournaments.rounds.mockResolvedValue(rounds(2, 2, 0));
        renderApp('/admin/tournaments/t-1');

        const card = await progressCard();
        await waitFor(() => expect(card).toHaveTextContent('Runde 2 von 2'));
        expect(within(card).getByRole('button', { name: 'Runde entfernen' })).toBeDisabled();
        expect(within(card).getByRole('button', { name: 'Runde hinzufügen' })).toBeEnabled();
    });

    it('cannot step above 20 rounds', async () => {
        tournaments.get.mockResolvedValue(tournament('ACTIVE', 20));
        tournaments.rounds.mockResolvedValue(rounds(2, 2, 0));
        renderApp('/admin/tournaments/t-1');

        const card = await progressCard();
        await waitFor(() => expect(card).toHaveTextContent('Runde 2 von 20'));
        expect(within(card).getByRole('button', { name: 'Runde hinzufügen' })).toBeDisabled();
    });

    it('rolls a rejected step back and explains why', async () => {
        tournaments.get.mockResolvedValue(tournament('ACTIVE'));
        tournaments.rounds.mockResolvedValue(rounds(2, 2, 0));
        tournaments.setTotalRounds.mockRejectedValue(
            new ApiError({
                code: 'TOTAL_ROUNDS_BELOW_ROUND_COUNT',
                status: 409,
                field: 'totalRounds',
                message: 'below',
            }),
        );
        renderApp('/admin/tournaments/t-1');

        const card = await progressCard();
        await waitFor(() => expect(card).toHaveTextContent('Runde 2 von 4'));
        await userEvent.click(within(card).getByRole('button', { name: 'Runde entfernen' }));

        expect(
            await within(card).findByText(
                'Die Rundenzahl darf nicht unter den bereits ausgelosten Runden liegen.',
            ),
        ).toBeInTheDocument();
        expect(card).toHaveTextContent('Runde 2 von 4');
    });

    it('is read-only once the tournament is completed, leaving only the publish action', async () => {
        tournaments.get.mockResolvedValue(tournament('COMPLETED'));
        tournaments.rounds.mockResolvedValue(rounds(4, 2, 2));
        renderApp('/admin/tournaments/t-1');

        const card = await progressCard();
        await waitFor(() => expect(card).toHaveTextContent('Runde 4 von 4'));
        expect(within(card).queryByRole('button', { name: 'Runde hinzufügen' })).not.toBeInTheDocument();
        expect(within(card).queryByRole('button', { name: 'Runde entfernen' })).not.toBeInTheDocument();
        expect(within(card).getAllByRole('button')).toHaveLength(1);
    });

    it('does not exist in setup, where the info card lists the total rounds', async () => {
        tournaments.get.mockResolvedValue(tournament('SETUP', 6));
        tournaments.rounds.mockResolvedValue([]);
        renderApp('/admin/tournaments/t-1');

        expect(await screen.findByText('Runden insgesamt: 6')).toBeInTheDocument();
        expect(screen.queryByRole('region', { name: 'Fortschritt' })).not.toBeInTheDocument();
    });
});

describe('next round action', () => {
    async function progressCard() {
        return screen.findByRole('region', { name: 'Fortschritt' });
    }

    it('starts the next round after confirmation and shows it on the Rounds tab', async () => {
        const before = rounds(2, 2, 2);
        const next: Round = { number: 3, tables: [table(1, playing), table(2, playing)] };
        tournaments.get.mockResolvedValue(tournament('ACTIVE'));
        tournaments.rounds.mockResolvedValueOnce(before).mockResolvedValue([...before, next]);
        tournaments.startNextRound.mockResolvedValue(next);
        renderApp('/admin/tournaments/t-1?tab=standings');

        const card = await progressCard();
        await userEvent.click(
            await within(card).findByRole('button', { name: 'Nächste Runde starten' }),
        );
        const dialog = await screen.findByRole('dialog');
        expect(dialog).toHaveTextContent(
            'Die Paarungen werden aus der aktuellen Rangliste ausgelost.',
        );
        await userEvent.click(within(dialog).getByRole('button', { name: 'Bestätigen' }));

        expect(tournaments.startNextRound).toHaveBeenCalledWith('t-1');
        expect(await screen.findByRole('heading', { name: 'Runde 3' })).toBeInTheDocument();
        expect(screen.getByRole('tab', { name: 'Runden (3)' })).toHaveAttribute(
            'aria-selected',
            'true',
        );
    });

    it('selects the new round even when an older round was picked', async () => {
        const before = rounds(2, 2, 2);
        const next: Round = { number: 3, tables: [table(1, playing), table(2, playing)] };
        tournaments.get.mockResolvedValue(tournament('ACTIVE'));
        tournaments.rounds.mockResolvedValueOnce(before).mockResolvedValue([...before, next]);
        tournaments.startNextRound.mockResolvedValue(next);
        renderApp('/admin/tournaments/t-1?tab=rounds');

        await userEvent.click(await screen.findByRole('tab', { name: /^Runde 1/ }));
        expect(await screen.findByRole('heading', { name: 'Runde 1' })).toBeInTheDocument();

        const card = await progressCard();
        await userEvent.click(within(card).getByRole('button', { name: 'Nächste Runde starten' }));
        await userEvent.click(
            within(await screen.findByRole('dialog')).getByRole('button', { name: 'Bestätigen' }),
        );

        expect(await screen.findByRole('heading', { name: 'Runde 3' })).toBeInTheDocument();
    });

    it('is disabled with the number of open tables while the round is incomplete', async () => {
        tournaments.get.mockResolvedValue(tournament('ACTIVE'));
        tournaments.rounds.mockResolvedValue(rounds(2, 3, 2));
        renderApp('/admin/tournaments/t-1');

        const card = await progressCard();
        expect(
            await within(card).findByRole('button', { name: 'Nächste Runde starten' }),
        ).toBeDisabled();
        expect(card).toHaveTextContent('Noch offene Tische: 1');
    });

    it('is not offered once the tournament is finished', async () => {
        tournaments.get.mockResolvedValue(tournament('COMPLETED'));
        tournaments.rounds.mockResolvedValue(rounds(4, 2, 2));
        renderApp('/admin/tournaments/t-1');

        const card = await progressCard();
        await waitFor(() => expect(card).toHaveTextContent('Runde 4 von 4'));
        expect(
            within(card).queryByRole('button', { name: 'Nächste Runde starten' }),
        ).not.toBeInTheDocument();
        expect(within(card).queryByText(/Letzte Runde/)).not.toBeInTheDocument();
    });

    it('gives way to the finish action in the last round', async () => {
        tournaments.get.mockResolvedValue(tournament('ACTIVE', 2));
        tournaments.rounds.mockResolvedValue(rounds(2, 2, 2));
        renderApp('/admin/tournaments/t-1');

        const card = await progressCard();
        expect(
            await within(card).findByRole('button', { name: 'Turnier abschließen' }),
        ).toBeEnabled();
        expect(
            within(card).queryByRole('button', { name: 'Nächste Runde starten' }),
        ).not.toBeInTheDocument();
    });

    it('shows the localized reason when the server rejects the advance', async () => {
        tournaments.get.mockResolvedValue(tournament('ACTIVE'));
        tournaments.rounds.mockResolvedValue(rounds(2, 2, 2));
        tournaments.startNextRound.mockRejectedValue(
            new ApiError({
                code: 'ROUND_INCOMPLETE',
                status: 409,
                field: null,
                message: 'incomplete',
            }),
        );
        renderApp('/admin/tournaments/t-1');

        const card = await progressCard();
        await userEvent.click(
            await within(card).findByRole('button', { name: 'Nächste Runde starten' }),
        );
        await userEvent.click(
            within(await screen.findByRole('dialog')).getByRole('button', { name: 'Bestätigen' }),
        );

        expect(
            await within(card).findByText(
                'Die nächste Runde kann erst starten, wenn alle Tische abgeschlossen sind.',
            ),
        ).toBeInTheDocument();
    });
});
