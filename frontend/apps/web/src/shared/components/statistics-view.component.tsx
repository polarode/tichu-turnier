import { useId, useState } from 'react';

import { buttonVariants } from '@tichu/ui/button.tsx';
import { DataTable, type DataTableColumn } from '@tichu/ui/data-table.tsx';
import {
    DropdownMenu,
    DropdownMenuCheckboxItem,
    DropdownMenuContent,
    DropdownMenuLabel,
    DropdownMenuSeparator,
    DropdownMenuTrigger,
} from '@tichu/ui/dropdown-menu.tsx';

import type {
    Highlight,
    TeamStatistics,
    TournamentStatistics,
} from '@/domain/statistics.domain.ts';
import {
    COMPACT_STATISTICS_COLUMNS,
    DESKTOP_MEDIA_QUERY,
    STATISTICS_COLUMN_IDS,
    type StatisticsColumn,
} from '@/shared/constants/statistics-columns.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';
import type { TranslationKey } from '@/shared/i18n/catalogs/en.ts';
import { useStatisticsColumnsStore } from '@/shared/stores/statistics-columns.store.ts';

interface StatisticsViewProps {
    statistics: TournamentStatistics;
}

/** The catalog key of each Highlight metric's label. */
const METRIC_KEYS = {
    SUCCESSFUL_TICHU: 'statistics.metric.SUCCESSFUL_TICHU',
    BOMBS: 'statistics.metric.BOMBS',
    DOUBLE_WINS: 'statistics.metric.DOUBLE_WINS',
    TICHU_CALLS: 'statistics.metric.TICHU_CALLS',
    DESTROYED_TICHUS: 'statistics.metric.DESTROYED_TICHUS',
    BESCHISS_GAMES: 'statistics.metric.BESCHISS_GAMES',
    GOOD_HORSE: 'statistics.metric.GOOD_HORSE',
} as const satisfies Record<Highlight['metric'], TranslationKey>;

/** The order the Highlight tiles appear in; the order the server sends them in is ignored. */
const HIGHLIGHT_ORDER: readonly Highlight['metric'][] = [
    'TICHU_CALLS',
    'SUCCESSFUL_TICHU',
    'DESTROYED_TICHUS',
    'DOUBLE_WINS',
    'BOMBS',
    'GOOD_HORSE',
    'BESCHISS_GAMES',
];

/** The formatters a team table cell may use, in the current locale. */
interface CellFormat {
    t: (key: TranslationKey) => string;
    decimal: Intl.NumberFormat;
}

/** One column of the team table: its id, its header key, its raw figure and how it renders a team. */
interface StatisticsColumnDef {
    id: StatisticsColumn;
    header: TranslationKey;
    /** The raw figure a click sorts by; null means no value and sorts last. Never the formatted text. */
    value: (team: TeamStatistics) => number | null;
    cell: (team: TeamStatistics, format: CellFormat) => string | number;
}

const noValueOr = (value: number | null, { t, decimal }: CellFormat) =>
    value === null ? t('statistics.noValue') : decimal.format(value);

/** The team table columns after the always-shown Team column, in display order. */
const STATISTICS_COLUMNS: readonly StatisticsColumnDef[] = [
    {
        id: 'tichuCalls',
        header: 'statistics.tichuCalls',
        value: (team) => team.tichuCalls,
        cell: (team) => team.tichuCalls,
    },
    {
        id: 'successfulSmallTichu',
        header: 'statistics.smallTichu',
        value: (team) => team.successfulSmallTichu,
        cell: (team) => team.successfulSmallTichu,
    },
    {
        id: 'successfulGrandTichu',
        header: 'statistics.grandTichu',
        value: (team) => team.successfulGrandTichu,
        cell: (team) => team.successfulGrandTichu,
    },
    {
        id: 'destroyedTichus',
        header: 'statistics.destroyedTichus',
        value: (team) => team.destroyedTichus,
        cell: (team) => team.destroyedTichus,
    },
    {
        id: 'bombCount',
        header: 'statistics.bombs',
        value: (team) => team.bombCount,
        cell: (team) => team.bombCount,
    },
    {
        id: 'bombsPerGame',
        header: 'statistics.bombsPerGame',
        value: (team) => team.bombsPerGame,
        cell: (team, format) => noValueOr(team.bombsPerGame, format),
    },
    {
        id: 'doubleWins',
        header: 'statistics.doubleWins',
        value: (team) => team.doubleWins,
        cell: (team) => team.doubleWins,
    },
    {
        id: 'beschissGames',
        header: 'statistics.beschissGames',
        value: (team) => team.beschissGames,
        cell: (team) => team.beschissGames,
    },
    {
        id: 'goodHorseRatio',
        header: 'statistics.goodHorse',
        value: (team) => team.goodHorseRatio,
        cell: (team, format) => noValueOr(team.goodHorseRatio, format),
    },
];

/** The columns a viewer sees before choosing: all on a desktop-width viewport, the core four otherwise. */
function deviceDefaultColumns(): readonly StatisticsColumn[] {
    return window.matchMedia(DESKTOP_MEDIA_QUERY).matches
        ? STATISTICS_COLUMN_IDS
        : COMPACT_STATISTICS_COLUMNS;
}

/**
 * The statistics of one tournament (glossary: Statistics): the Highlights and the per-team
 * table; the Team Trend sits with the ranking instead. It renders the statistics it is given and fetches nothing, so
 * the organizer's and the public tab share one markup; only the viewer's column choice
 * comes from the statistics-columns store. Figures are per team only, never per player.
 */
export function StatisticsView({ statistics }: StatisticsViewProps) {
    const { t, locale } = useI18n();
    const highlightsId = useId();
    const { teams } = statistics;
    const highlights = [...statistics.highlights].sort(
        (a, b) => HIGHLIGHT_ORDER.indexOf(a.metric) - HIGHLIGHT_ORDER.indexOf(b.metric),
    );
    const storedColumns = useStatisticsColumnsStore((state) => state.visible);
    const toggleColumn = useStatisticsColumnsStore((state) => state.toggle);
    // Resolved once per mount, so resizing or rotating the device never changes the table.
    const [deviceColumns] = useState(deviceDefaultColumns);
    const visibleColumns = storedColumns ?? deviceColumns;

    const decimal = new Intl.NumberFormat(locale, {
        minimumFractionDigits: 1,
        maximumFractionDigits: 1,
    });
    const integer = new Intl.NumberFormat(locale, { maximumFractionDigits: 0 });
    const format: CellFormat = { t, decimal };
    const teamColumns: DataTableColumn<TeamStatistics>[] = [
        {
            id: 'team',
            header: t('statistics.team'),
            cell: (team) => team.teamName,
            sortValue: (team) => team.teamName,
            sortFirst: 'asc',
            rowHeader: true,
            headClassName: 'sticky left-0 bg-card',
            cellClassName: 'sticky left-0 bg-card font-medium',
        },
        ...STATISTICS_COLUMNS.map((column) => ({
            id: column.id,
            header: t(column.header),
            cell: (team: TeamStatistics) => column.cell(team, format),
            sortValue: column.value,
            align: 'end' as const,
        })),
    ];
    const formatHighlight = (highlight: Highlight) =>
        highlight.metric === 'GOOD_HORSE'
            ? decimal.format(highlight.value)
            : integer.format(highlight.value);

    return (
        <div className="flex flex-col gap-4">
            {highlights.length > 0 ? (
                <section aria-labelledby={highlightsId} className="flex flex-col gap-3">
                    <h2 id={highlightsId} className="text-base font-medium">
                        {t('statistics.highlights')}
                    </h2>
                    <ul className="grid gap-2 grid-cols-2 sm:grid-cols-3 lg:grid-cols-4">
                        {highlights.map((highlight) => (
                            <li
                                key={highlight.metric}
                                className="flex flex-col gap-1 rounded-md border border-border bg-card p-3"
                            >
                                <span className="text-sm text-muted-foreground">
                                    {t(METRIC_KEYS[highlight.metric])}
                                </span>
                                <span className="text-2xl font-semibold tabular-nums">
                                    {formatHighlight(highlight)}
                                </span>
                                <span className="text-sm font-medium">
                                    {highlight.teams.map((team) => team.teamName).join(', ')}
                                </span>
                            </li>
                        ))}
                    </ul>
                </section>
            ) : null}

            <div className="flex flex-col gap-2">
                <div className="flex justify-end">
                    <DropdownMenu>
                        <DropdownMenuTrigger
                            className={buttonVariants({ variant: 'outline', size: 'sm' })}
                        >
                            {t('statistics.columns')}
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end">
                            <DropdownMenuLabel>{t('statistics.columnsLabel')}</DropdownMenuLabel>
                            <DropdownMenuSeparator />
                            {STATISTICS_COLUMNS.map((column) => (
                                <DropdownMenuCheckboxItem
                                    key={column.id}
                                    checked={visibleColumns.includes(column.id)}
                                    onCheckedChange={() => toggleColumn(column.id, visibleColumns)}
                                >
                                    {t(column.header)}
                                </DropdownMenuCheckboxItem>
                            ))}
                        </DropdownMenuContent>
                    </DropdownMenu>
                </div>
                <div className="rounded-md border border-border bg-card">
                    <DataTable
                        className="text-sm tabular-nums"
                        columns={teamColumns}
                        rows={teams}
                        getRowId={(team) => team.teamId}
                        caption={t('statistics.title')}
                        locale={locale}
                        visibleColumns={['team', ...visibleColumns]}
                    />
                </div>
            </div>
        </div>
    );
}
