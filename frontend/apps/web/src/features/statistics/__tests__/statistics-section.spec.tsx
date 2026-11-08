import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

import { screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthRepository } from '@/auth-api/auth.repository.ts';
import type { TableConfirmation } from '@/domain/game.domain.ts';
import type { Round, TableOverview } from '@/domain/round.domain.ts';
import type { Tournament } from '@/domain/tournament.domain.ts';
import { ApiError } from '@/shared/api/api-error.ts';
import { useStatisticsColumnsStore } from '@/shared/stores/statistics-columns.store.ts';
import { TeamRepository } from '@/team-api/team.repository.ts';
import { statisticsDtoSchema } from '@/statistics-api/dtos/statistics.dto.ts';
import { statisticsDtoToDomain } from '@/statistics-api/mappers/statistics.mapper.ts';
import { StatisticsRepository } from '@/statistics-api/statistics.repository.ts';
import { TournamentRepository } from '@/tournament-api/tournament.repository.ts';
import { installMatchMedia, type FakeMatchMedia } from '@/test-helpers/match-media.ts';
import { renderApp } from '@/test-helpers/render-app.tsx';
import { bodyRows } from '@/test-helpers/table-rows.ts';
import { TableStatus } from '@/vocabulary/table-status.enum.ts';

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
vi.mock('@/statistics-api/statistics.repository.ts', () => ({
    StatisticsRepository: { organizer: vi.fn(), publicStatistics: vi.fn() },
}));

const auth = vi.mocked(AuthRepository);
const tournaments = vi.mocked(TournamentRepository);
const teams = vi.mocked(TeamRepository);
const statistics = vi.mocked(StatisticsRepository);

/** The shared contract fixture, the same wire body the backend's integration test answers. */
const fixture = JSON.parse(
    readFileSync(
        resolve(__dirname, '../../../../../../../contract-fixtures/statistics.json'),
        'utf8',
    ),
) as { response: unknown };

function tournament(status: Tournament['status']): Tournament {
    return {
        id: 't-1',
        name: 'Jahresturnier',
        description: '',
        maxTeams: 16,
        settings: { grandTichuBonus: 200, doubleWinBonus: 200, totalRounds: 4 },
        status,
        createdAt: new Date('2026-01-01T00:00:00Z'),
        publicVisibility: false,
        updatedAt: new Date('2026-01-01T00:00:00Z'),
    };
}

const completed: TableConfirmation = {
    status: TableStatus.COMPLETED,
    confirmedTeamIds: ['a', 'b'],
};

function table(tableNumber: number): TableOverview {
    const schedule = [1, 2, 3, 4].map((gameNumber) => ({
        gameNumber,
        teamIds: ['a', 'b'] as [string, string],
    }));
    return {
        tableNumber,
        teamIds: ['a', 'b'],
        trio: false,
        confirmation: completed,
        gameCount: 4,
        schedule,
        games: [],
    };
}

const rounds: Round[] = [{ number: 1, tables: [table(1)] }];

let viewport: FakeMatchMedia;

beforeEach(() => {
    viewport = installMatchMedia({ dark: false, width: 1024 });
    window.localStorage.clear();
    useStatisticsColumnsStore.setState({ visible: null });
    vi.clearAllMocks();
    auth.refresh.mockResolvedValue({ id: 'admin-1', email: '[EMAIL]', role: 'ADMIN' });
    teams.list.mockResolvedValue([]);
    tournaments.get.mockResolvedValue(tournament('ACTIVE'));
    tournaments.rounds.mockResolvedValue(rounds);
});

afterEach(() => {
    viewport.uninstall();
    window.localStorage.clear();
});

describe('statistics tab', () => {
    it('shows the organizer statistics of the tournament', async () => {
        statistics.organizer.mockImplementation(async () =>
            statisticsDtoToDomain(statisticsDtoSchema.parse(fixture.response)),
        );
        renderApp('/admin/tournaments/t-1?tab=statistics');

        const statisticsTable = await screen.findByRole('table', { name: 'Statistiken' });
        // Directly under the page's h1, like the other tabs' headings.
        expect(screen.getByRole('heading', { level: 2, name: 'Verlauf' })).toBeInTheDocument();
        expect(statistics.organizer).toHaveBeenCalledWith('t-1');
        expect(bodyRows(statisticsTable)[0]).toEqual([
            'Badgers',
            '2',
            '2',
            '0',
            '1',
            '3',
            '0,4',
            '1',
            '1',
            '79,4',
        ]);
        // Wolves has no completed table (bombs per game) and no Good Horse ratio: two dashes.
        expect(within(statisticsTable).getAllByText('—')).toHaveLength(2);
    });

    it('sits after Standings and before Teams', async () => {
        statistics.organizer.mockResolvedValue(
            statisticsDtoToDomain(statisticsDtoSchema.parse(fixture.response)),
        );
        renderApp('/admin/tournaments/t-1');

        const tabs = await screen.findByRole('tablist', { name: 'Turnierbereiche' });
        await waitFor(() =>
            expect(
                within(tabs)
                    .getAllByRole('tab')
                    .map((tab) => tab.textContent),
            ).toEqual(['Runden (1)', 'Rangliste', 'Statistiken', 'Teams (0)']),
        );
    });

    it('shows the localized error when the statistics cannot be loaded', async () => {
        statistics.organizer.mockRejectedValue(
            new ApiError({ code: 'UNKNOWN', status: 500, field: null, message: 'boom' }),
        );
        renderApp('/admin/tournaments/t-1?tab=statistics');

        expect(
            await screen.findByText('Etwas ist schiefgelaufen. Bitte erneut versuchen.'),
        ).toBeInTheDocument();
        expect(screen.queryByRole('table', { name: 'Statistiken' })).not.toBeInTheDocument();
    });
});
