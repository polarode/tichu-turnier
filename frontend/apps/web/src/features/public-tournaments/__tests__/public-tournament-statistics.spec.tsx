import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthRepository } from '@/auth-api/auth.repository.ts';
import type { PublicTournamentDetail } from '@/domain/tournament.domain.ts';
import { ApiError } from '@/shared/api/api-error.ts';
import { useStatisticsColumnsStore } from '@/shared/stores/statistics-columns.store.ts';
import { statisticsDtoSchema } from '@/statistics-api/dtos/statistics.dto.ts';
import { statisticsDtoToDomain } from '@/statistics-api/mappers/statistics.mapper.ts';
import { StatisticsRepository } from '@/statistics-api/statistics.repository.ts';
import { publicTournamentDetailDtoSchema } from '@/tournament-api/dtos/public-tournament.dto.ts';
import { publicTournamentDetailDtoToDomain } from '@/tournament-api/mappers/public-tournament.mapper.ts';
import { TournamentRepository } from '@/tournament-api/tournament.repository.ts';
import { installMatchMedia, type FakeMatchMedia } from '@/test-helpers/match-media.ts';
import { renderApp } from '@/test-helpers/render-app.tsx';
import { bodyRows } from '@/test-helpers/table-rows.ts';
import { TournamentStatus } from '@/vocabulary/tournament-status.enum.ts';

vi.mock('@/auth-api/auth.repository.ts', () => ({
    AuthRepository: { register: vi.fn(), login: vi.fn(), refresh: vi.fn(), logout: vi.fn() },
}));
vi.mock('@/tournament-api/tournament.repository.ts', async () => {
    const { mockTournamentRepositoryModule } =
        await import('@/test-helpers/tournament-repository.mock.ts');
    return mockTournamentRepositoryModule();
});
vi.mock('@/statistics-api/statistics.repository.ts', () => ({
    StatisticsRepository: { organizer: vi.fn(), publicStatistics: vi.fn() },
}));

const auth = vi.mocked(AuthRepository);
const tournaments = vi.mocked(TournamentRepository);
const statistics = vi.mocked(StatisticsRepository);

/** The shared contract fixtures: a published tournament and its statistics. */
const publishedFixture = JSON.parse(
    readFileSync(
        resolve(
            __dirname,
            '../../../../../../../contract-fixtures/public-tournament-published.json',
        ),
        'utf8',
    ),
) as { response: unknown };
const harvestCup = publicTournamentDetailDtoToDomain(
    publicTournamentDetailDtoSchema.parse(publishedFixture.response),
);

const statisticsFixture = JSON.parse(
    readFileSync(
        resolve(__dirname, '../../../../../../../contract-fixtures/statistics.json'),
        'utf8',
    ),
) as { response: unknown };
const harvestStatistics = statisticsDtoToDomain(
    statisticsDtoSchema.parse(statisticsFixture.response),
);

const noSession = new ApiError({
    code: 'UNAUTHENTICATED',
    status: 401,
    field: null,
    message: 'no session',
});

const activeCup: PublicTournamentDetail = { ...harvestCup, status: TournamentStatus.ACTIVE };

function tabsOf(): HTMLElement {
    return screen.getByRole('tablist', { name: 'Tournament sections' });
}

describe('public tournament statistics tab', () => {
    let viewport: FakeMatchMedia;

    beforeEach(() => {
        viewport = installMatchMedia({ dark: false, width: 1024 });
        window.localStorage.clear();
        useStatisticsColumnsStore.setState({ visible: null });
        vi.resetAllMocks();
        auth.refresh.mockRejectedValue(noSession);
        statistics.publicStatistics.mockResolvedValue(harvestStatistics);
    });

    afterEach(() => {
        viewport.uninstall();
        window.localStorage.clear();
    });

    it('offers a Results and a Statistics tab once the tournament is published', async () => {
        tournaments.getPublicTournament.mockResolvedValue(harvestCup);

        renderApp('/public/tournaments/t-harvest-cup', { initialLocale: 'en' });

        await screen.findByRole('heading', { name: 'Harvest Cup' });
        const tabs = tabsOf();
        expect(
            within(tabs)
                .getAllByRole('tab')
                .map((tab) => tab.textContent),
        ).toEqual(['Results', 'Statistics']);
        expect(screen.getByRole('tab', { name: 'Results' })).toHaveAttribute(
            'aria-selected',
            'true',
        );
        expect(
            screen.getByRole('heading', { level: 2, name: 'Final Ranking' }),
        ).toBeInTheDocument();
    });

    it('loads the statistics only when the Statistics tab is selected', async () => {
        tournaments.getPublicTournament.mockResolvedValue(harvestCup);

        renderApp('/public/tournaments/t-harvest-cup', { initialLocale: 'en' });

        await screen.findByRole('tab', { name: 'Statistics' });
        expect(statistics.publicStatistics).not.toHaveBeenCalled();

        await userEvent.click(screen.getByRole('tab', { name: 'Statistics' }));

        const statisticsTable = await screen.findByRole('table', { name: 'Statistics' });
        expect(statistics.publicStatistics).toHaveBeenCalledWith('t-harvest-cup');
        expect(bodyRows(statisticsTable)[0]).toEqual([
            'Badgers',
            '2',
            '2',
            '0',
            '1',
            '3',
            '0.4',
            '1',
            '1',
            '79.4',
        ]);
        expect(screen.queryByRole('heading', { level: 2, name: 'Final Ranking' })).toBeNull();
    });

    it('opens the Statistics tab directly from the URL', async () => {
        tournaments.getPublicTournament.mockResolvedValue(harvestCup);

        renderApp('/public/tournaments/t-harvest-cup?tab=statistics', { initialLocale: 'en' });

        const statisticsTable = await screen.findByRole('table', { name: 'Statistics' });
        expect(bodyRows(statisticsTable)[0]).toEqual([
            'Badgers',
            '2',
            '2',
            '0',
            '1',
            '3',
            '0.4',
            '1',
            '1',
            '79.4',
        ]);
        // Directly under the tournament's h1, like the Final Ranking on the Results tab.
        expect(screen.getByRole('heading', { level: 2, name: 'Trend' })).toBeInTheDocument();
        expect(screen.getByRole('tab', { name: 'Statistics' })).toHaveAttribute(
            'aria-selected',
            'true',
        );
    });

    it('falls back to the Results tab for an unknown tab in the URL', async () => {
        tournaments.getPublicTournament.mockResolvedValue(harvestCup);

        renderApp('/public/tournaments/t-harvest-cup?tab=bogus', { initialLocale: 'en' });

        await screen.findByRole('heading', { level: 2, name: 'Final Ranking' });
        expect(statistics.publicStatistics).not.toHaveBeenCalled();
    });

    it('shows no tabs and never asks for statistics while the tournament is active', async () => {
        tournaments.getPublicTournament.mockResolvedValue(activeCup);

        renderApp('/public/tournaments/t-harvest-cup', { initialLocale: 'en' });

        await screen.findByRole('heading', { name: 'Harvest Cup' });
        expect(screen.queryByRole('tablist')).toBeNull();
        expect(screen.queryByRole('tab', { name: 'Statistics' })).toBeNull();
        expect(statistics.publicStatistics).not.toHaveBeenCalled();
    });

    it('shows the normal page for ?tab=statistics on an active tournament', async () => {
        tournaments.getPublicTournament.mockResolvedValue(activeCup);

        renderApp('/public/tournaments/t-harvest-cup?tab=statistics', { initialLocale: 'en' });

        await screen.findByRole('heading', { name: 'Harvest Cup' });
        await waitFor(() => expect(screen.getByRole('region', { name: 'Round 1' })).toBeVisible());
        expect(screen.queryByRole('table', { name: 'Statistics' })).toBeNull();
        expect(statistics.publicStatistics).not.toHaveBeenCalled();
    });

    it('shows the localized error when the statistics cannot be loaded', async () => {
        tournaments.getPublicTournament.mockResolvedValue(harvestCup);
        statistics.publicStatistics.mockRejectedValue(
            new ApiError({ code: 'UNKNOWN', status: 500, field: null, message: 'boom' }),
        );

        renderApp('/public/tournaments/t-harvest-cup?tab=statistics', { initialLocale: 'en' });

        expect(await screen.findByRole('alert')).toHaveTextContent(
            'Something went wrong. Please try again.',
        );
        expect(screen.queryByRole('table', { name: 'Statistics' })).toBeNull();
    });
});
