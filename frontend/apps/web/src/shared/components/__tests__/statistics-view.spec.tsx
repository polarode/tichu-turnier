import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

import { screen, within } from '@testing-library/react';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';

import type { TournamentStatistics } from '@/domain/statistics.domain.ts';
import { statisticsDtoSchema } from '@/statistics-api/dtos/statistics.dto.ts';
import { statisticsDtoToDomain } from '@/statistics-api/mappers/statistics.mapper.ts';
import { StatisticsView } from '@/shared/components/statistics-view.component.tsx';
import { renderWithProviders } from '@/test-helpers/render-with-providers.tsx';
import { bodyRows, columnHeaders } from '@/test-helpers/table-rows.ts';

/**
 * The shared contract fixture: the backend asserts its API answers exactly `response`;
 * here the same wire data runs through the real zod schema and mapper into the view.
 */
const fixture = JSON.parse(
    readFileSync(
        resolve(__dirname, '../../../../../../../contract-fixtures/statistics.json'),
        'utf8',
    ),
) as { response: unknown };

const statistics: TournamentStatistics = statisticsDtoToDomain(
    statisticsDtoSchema.parse(fixture.response),
);

/**
 * Recharts' ResponsiveContainer only renders once its container reports a size, and
 * happy-dom reports none. The stub reports one fixed size, so the chart mounts.
 */
class FixedSizeResizeObserver {
    constructor(private readonly callback: ResizeObserverCallback) {}
    observe(target: Element) {
        this.callback(
            [{ target, contentRect: { width: 600, height: 300 } } as ResizeObserverEntry],
            this as unknown as ResizeObserver,
        );
    }
    unobserve() {}
    disconnect() {}
}

const originalResizeObserver = globalThis.ResizeObserver;

beforeAll(() => {
    globalThis.ResizeObserver = FixedSizeResizeObserver as unknown as typeof ResizeObserver;
});

afterAll(() => {
    globalThis.ResizeObserver = originalResizeObserver;
});

describe('StatisticsView', () => {
    it('names every tied team in a highlight', () => {
        renderWithProviders(<StatisticsView statistics={statistics} />);

        // Four teams tie at two successful Tichu calls; the tie lists them all, in server order.
        expect(screen.getByText('Erfolgreiche Tichus')).toBeInTheDocument();
        expect(screen.getByText('Badgers, Falcons, Owls, Ravens')).toBeInTheDocument();
        expect(screen.getByText('Falcons, Ravens')).toBeInTheDocument();
        expect(screen.getByText('Badgers, Falcons')).toBeInTheDocument();
    });

    it('renders each team row with its literal values from the fixture', () => {
        renderWithProviders(<StatisticsView statistics={statistics} />);

        const table = screen.getByRole('table', { name: 'Statistiken' });
        expect(columnHeaders(table)).toEqual([
            'Team',
            'Erfolgreiche kleine Tichus',
            'Erfolgreiche große Tichus',
            'Bomben',
            'Bomben pro Spiel',
            'Doppelsiege',
        ]);
        expect(bodyRows(table)).toEqual([
            ['Badgers', '2', '0', '3', '0,4', '1'],
            ['Falcons', '1', '1', '4', '0,5', '1'],
            ['Owls', '1', '1', '3', '0,4', '0'],
            ['Ravens', '1', '1', '4', '0,5', '0'],
            ['Wolves', '0', '0', '0', '—', '0'],
        ]);
    });

    it('shows an em dash instead of bombs per game for a team without a completed table', () => {
        renderWithProviders(<StatisticsView statistics={statistics} />);

        const table = screen.getByRole('table', { name: 'Statistiken' });
        const wolves = bodyRows(table).find((row) => row[0] === 'Wolves');
        expect(wolves?.[4]).toBe('—');
    });

    it('gives screen readers the cumulative Victory Points per round in a table', () => {
        renderWithProviders(<StatisticsView statistics={statistics} />);

        const trend = screen.getByRole('table', { name: 'Kumulierte Siegpunkte je Runde' });
        expect(columnHeaders(trend)).toEqual([
            'Runde',
            'Badgers',
            'Falcons',
            'Owls',
            'Ravens',
            'Wolves',
        ]);
        // Round 2 is the cumulative total: Falcons reached 9 VP after two rounds.
        expect(bodyRows(trend)).toEqual([
            ['1', '4', '6', '3', '4', '—'],
            ['2', '9', '9', '7', '9', '—'],
        ]);
        expect(within(trend).getByRole('rowheader', { name: '2' })).toBeInTheDocument();
    });

    it('shows an empty state instead of the trend chart before any round is completed', () => {
        renderWithProviders(
            <StatisticsView
                statistics={{
                    ...statistics,
                    highlights: [],
                    trends: statistics.trends.map((trend) => ({ ...trend, points: [] })),
                }}
            />,
        );

        expect(
            screen.getByText('Der Verlauf erscheint, sobald die erste Runde abgeschlossen ist.'),
        ).toBeInTheDocument();
        expect(
            screen.queryByRole('table', { name: 'Kumulierte Siegpunkte je Runde' }),
        ).not.toBeInTheDocument();
        expect(screen.queryByText('Bestwerte')).not.toBeInTheDocument();
    });
});
