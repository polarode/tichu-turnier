import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it } from 'vitest';

import type { TournamentStatistics } from '@/domain/statistics.domain.ts';
import { statisticsDtoSchema } from '@/statistics-api/dtos/statistics.dto.ts';
import { statisticsDtoToDomain } from '@/statistics-api/mappers/statistics.mapper.ts';
import { StatisticsView } from '@/shared/components/statistics-view.component.tsx';
import { TeamTrend } from '@/shared/components/team-trend.component.tsx';
import {
    STATISTICS_COLUMNS_STORAGE_KEY,
    useStatisticsColumnsStore,
} from '@/shared/stores/statistics-columns.store.ts';
import { installMatchMedia, type FakeMatchMedia } from '@/test-helpers/match-media.ts';
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

/** The header row of the team table on a phone: Team plus the four default columns. */
const COMPACT_HEADERS = [
    'Team',
    'Tichu-Ansagen',
    'Erfolgreiche kleine Tichus',
    'Erfolgreiche große Tichus',
    'Zerstörte Tichus',
];

const ALL_HEADERS = [
    'Team',
    'Tichu-Ansagen',
    'Erfolgreiche kleine Tichus',
    'Erfolgreiche große Tichus',
    'Zerstörte Tichus',
    'Bomben',
    'Bomben pro Spiel',
    'Doppelsiege',
    'Beschiss-Spiele',
    'Gutes Pferd (TP pro SP)',
];

const teamTable = () => screen.getByRole('table', { name: 'Statistiken' });

let viewport: FakeMatchMedia;

beforeAll(() => {
    globalThis.ResizeObserver = FixedSizeResizeObserver as unknown as typeof ResizeObserver;
});

afterAll(() => {
    globalThis.ResizeObserver = originalResizeObserver;
});

beforeEach(() => {
    viewport = installMatchMedia({ dark: false, width: 1024 });
    window.localStorage.clear();
    useStatisticsColumnsStore.setState({ visible: null });
});

afterEach(() => {
    viewport.uninstall();
    window.localStorage.clear();
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
            'Tichu-Ansagen',
            'Erfolgreiche kleine Tichus',
            'Erfolgreiche große Tichus',
            'Zerstörte Tichus',
            'Bomben',
            'Bomben pro Spiel',
            'Doppelsiege',
            'Beschiss-Spiele',
            'Gutes Pferd (TP pro SP)',
        ]);
        expect(bodyRows(table)).toEqual([
            ['Badgers', '2', '2', '0', '1', '3', '0,4', '1', '1', '79,4'],
            ['Falcons', '2', '1', '1', '0', '4', '0,5', '1', '1', '93,3'],
            ['Owls', '3', '1', '1', '0', '3', '0,4', '0', '0', '76,4'],
            ['Ravens', '2', '1', '1', '0', '4', '0,5', '0', '2', '78,9'],
            ['Wolves', '0', '0', '0', '0', '0', '—', '0', '0', '—'],
        ]);
    });

    it('orders the highlights by its own fixed order, whatever order the server sends', () => {
        renderWithProviders(
            <StatisticsView
                statistics={{ ...statistics, highlights: [...statistics.highlights].reverse() }}
            />,
        );

        const section = screen.getByRole('region', { name: 'Bestwerte' });
        const labels = within(section)
            .getAllByRole('listitem')
            .map((tile) => tile.querySelector('span')?.textContent);
        expect(labels).toEqual([
            'Tichu-Ansagen',
            'Erfolgreiche Tichus',
            'Zerstörte Tichus',
            'Doppelsiege',
            'Meiste Bomben',
            'Gutes Pferd',
            'Beschiss-Spiele',
        ]);
    });

    it('formats highlight counts as integers and the Good Horse with one decimal', () => {
        renderWithProviders(<StatisticsView statistics={statistics} />);

        // Each tile reads as [label, value, leading teams]; the fixture backs each value.
        const tileTexts = (label: string) =>
            Array.from(
                screen.getByText(label).closest('li')?.querySelectorAll('span') ?? [],
                (span) => span.textContent,
            );
        expect(tileTexts('Erfolgreiche Tichus')).toEqual([
            'Erfolgreiche Tichus',
            '2',
            'Badgers, Falcons, Owls, Ravens',
        ]);
        expect(tileTexts('Gutes Pferd')).toEqual(['Gutes Pferd', '76,4', 'Owls']);
    });

    it('shows an em dash instead of bombs per game for a team without a completed table', () => {
        renderWithProviders(<StatisticsView statistics={statistics} />);

        const table = screen.getByRole('table', { name: 'Statistiken' });
        const wolves = bodyRows(table).find((row) => row[0] === 'Wolves');
        expect(wolves?.[6]).toBe('—');
    });

    it('leaves the Team Trend to the ranking', () => {
        renderWithProviders(<StatisticsView statistics={statistics} />);

        expect(screen.queryByRole('heading', { name: 'Verlauf' })).not.toBeInTheDocument();
    });

    it('gives screen readers the cumulative Victory Points per round in a table', () => {
        renderWithProviders(<TeamTrend trends={statistics.trends} />);

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
            <TeamTrend trends={statistics.trends.map((trend) => ({ ...trend, points: [] }))} />,
        );

        expect(
            screen.getByText('Der Verlauf erscheint, sobald die erste Runde abgeschlossen ist.'),
        ).toBeInTheDocument();
        expect(
            screen.queryByRole('table', { name: 'Kumulierte Siegpunkte je Runde' }),
        ).not.toBeInTheDocument();
    });

    it('leaves out the highlights before any round is completed', () => {
        renderWithProviders(<StatisticsView statistics={{ ...statistics, highlights: [] }} />);

        expect(screen.queryByText('Bestwerte')).not.toBeInTheDocument();
    });

    describe('column selection', () => {
        it('shows every column on a wide viewport when the viewer never chose', () => {
            renderWithProviders(<StatisticsView statistics={statistics} />);

            expect(columnHeaders(teamTable())).toEqual(ALL_HEADERS);
        });

        it('shows Team and the four default columns on a narrow viewport', () => {
            viewport.uninstall();
            viewport = installMatchMedia({ dark: false, width: 390 });
            renderWithProviders(<StatisticsView statistics={statistics} />);

            expect(columnHeaders(teamTable())).toEqual(COMPACT_HEADERS);
        });

        it('keeps the device default when the viewport is resized after mount', () => {
            renderWithProviders(<StatisticsView statistics={statistics} />);

            viewport.setWidth(390);

            expect(columnHeaders(teamTable())).toEqual(ALL_HEADERS);
        });

        it('hides a column the viewer switches off and keeps that choice after a reload', async () => {
            const user = userEvent.setup();
            const first = renderWithProviders(<StatisticsView statistics={statistics} />);

            await user.click(screen.getByRole('button', { name: 'Spalten' }));
            await user.click(
                screen.getByRole('menuitemcheckbox', { name: 'Erfolgreiche kleine Tichus' }),
            );

            expect(columnHeaders(teamTable())).toEqual(
                ALL_HEADERS.filter((header) => header !== 'Erfolgreiche kleine Tichus'),
            );
            expect(
                JSON.parse(window.localStorage.getItem(STATISTICS_COLUMNS_STORAGE_KEY) ?? 'null')
                    .state.visible,
            ).not.toContain('successfulSmallTichu');

            // A reload: the store starts again from localStorage, not from its in-memory state.
            const saved = window.localStorage.getItem(STATISTICS_COLUMNS_STORAGE_KEY);
            first.unmount();
            useStatisticsColumnsStore.setState({ visible: null });
            window.localStorage.setItem(STATISTICS_COLUMNS_STORAGE_KEY, saved ?? '');
            useStatisticsColumnsStore.persist.rehydrate();
            renderWithProviders(<StatisticsView statistics={statistics} />);

            expect(columnHeaders(teamTable())).toEqual(
                ALL_HEADERS.filter((header) => header !== 'Erfolgreiche kleine Tichus'),
            );
        });

        it('lets a stored choice win over the narrow-viewport default', () => {
            viewport.uninstall();
            viewport = installMatchMedia({ dark: false, width: 390 });
            window.localStorage.setItem(
                STATISTICS_COLUMNS_STORAGE_KEY,
                JSON.stringify({ state: { visible: ['bombCount'] }, version: 0 }),
            );
            useStatisticsColumnsStore.persist.rehydrate();

            renderWithProviders(<StatisticsView statistics={statistics} />);

            expect(columnHeaders(teamTable())).toEqual(['Team', 'Bomben']);
        });

        it('ignores unknown column ids in the stored choice', () => {
            window.localStorage.setItem(
                STATISTICS_COLUMNS_STORAGE_KEY,
                JSON.stringify({ state: { visible: ['bogus', 'bombCount'] }, version: 0 }),
            );
            useStatisticsColumnsStore.persist.rehydrate();

            renderWithProviders(<StatisticsView statistics={statistics} />);

            expect(columnHeaders(teamTable())).toEqual(['Team', 'Bomben']);
        });
    });

    describe('team table sorting', () => {
        /** The full team rows of the fixture, in the literal values the table shows. */
        const ROWS = {
            Badgers: ['Badgers', '2', '2', '0', '1', '3', '0,4', '1', '1', '79,4'],
            Falcons: ['Falcons', '2', '1', '1', '0', '4', '0,5', '1', '1', '93,3'],
            Owls: ['Owls', '3', '1', '1', '0', '3', '0,4', '0', '0', '76,4'],
            Ravens: ['Ravens', '2', '1', '1', '0', '4', '0,5', '0', '2', '78,9'],
            Wolves: ['Wolves', '0', '0', '0', '0', '0', '—', '0', '0', '—'],
        };

        const goodHorseHeader = () =>
            within(teamTable()).getByRole('button', { name: 'Gutes Pferd (TP pro SP)' });

        it('sorts Good Horse descending with the missing value last, then ascending with it still last', async () => {
            const user = userEvent.setup();
            renderWithProviders(<StatisticsView statistics={statistics} />);

            await user.click(goodHorseHeader());
            expect(bodyRows(teamTable())).toEqual([
                ROWS.Falcons,
                ROWS.Badgers,
                ROWS.Ravens,
                ROWS.Owls,
                ROWS.Wolves,
            ]);

            await user.click(goodHorseHeader());
            expect(bodyRows(teamTable())).toEqual([
                ROWS.Owls,
                ROWS.Ravens,
                ROWS.Badgers,
                ROWS.Falcons,
                ROWS.Wolves,
            ]);
        });

        it('sorts numbers by value, not by their formatted text', async () => {
            const user = userEvent.setup();
            // As text, "10" sorts before "9" and "3"; as numbers, 10 leads.
            const teams = statistics.teams.map((team) =>
                team.teamId === 'team-badgers'
                    ? { ...team, tichuCalls: 9 }
                    : team.teamId === 'team-falcons'
                      ? { ...team, tichuCalls: 10 }
                      : team,
            );
            renderWithProviders(<StatisticsView statistics={{ ...statistics, teams }} />);

            await user.click(within(teamTable()).getByRole('button', { name: 'Tichu-Ansagen' }));

            expect(bodyRows(teamTable())).toEqual([
                ['Falcons', '10', '1', '1', '0', '4', '0,5', '1', '1', '93,3'],
                ['Badgers', '9', '2', '0', '1', '3', '0,4', '1', '1', '79,4'],
                ROWS.Owls,
                ROWS.Ravens,
                ROWS.Wolves,
            ]);
        });

        it('sorts by a visible column while a column hidden through the picker stays out of the table', async () => {
            const user = userEvent.setup();
            renderWithProviders(<StatisticsView statistics={statistics} />);

            await user.click(screen.getByRole('button', { name: 'Spalten' }));
            await user.click(screen.getByRole('menuitemcheckbox', { name: 'Bomben' }));
            expect(columnHeaders(teamTable())).toEqual(
                ALL_HEADERS.filter((header) => header !== 'Bomben'),
            );

            await user.click(goodHorseHeader());
            expect(bodyRows(teamTable())).toEqual([
                ['Falcons', '2', '1', '1', '0', '0,5', '1', '1', '93,3'],
                ['Badgers', '2', '2', '0', '1', '0,4', '1', '1', '79,4'],
                ['Ravens', '2', '1', '1', '0', '0,5', '0', '2', '78,9'],
                ['Owls', '3', '1', '1', '0', '0,4', '0', '0', '76,4'],
                ['Wolves', '0', '0', '0', '0', '—', '0', '0', '—'],
            ]);
        });

        it('sorts the team name A to Z on its first click', async () => {
            const user = userEvent.setup();
            renderWithProviders(<StatisticsView statistics={statistics} />);

            // Start from a non-alphabetical order so the Team click visibly sorts.
            await user.click(goodHorseHeader());
            await user.click(within(teamTable()).getByRole('button', { name: 'Team' }));

            expect(bodyRows(teamTable())).toEqual([
                ROWS.Badgers,
                ROWS.Falcons,
                ROWS.Owls,
                ROWS.Ravens,
                ROWS.Wolves,
            ]);
        });
    });
});
