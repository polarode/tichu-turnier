import { useId } from 'react';
import {
    CartesianGrid,
    Legend,
    Line,
    LineChart,
    ResponsiveContainer,
    Tooltip,
    XAxis,
    YAxis,
} from 'recharts';

import type { Highlight, TournamentStatistics } from '@/domain/statistics.domain.ts';
import { trendColor } from '@/shared/constants/trend-palette.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';
import type { TranslationKey } from '@/shared/i18n/catalogs/en.ts';

interface StatisticsViewProps {
    statistics: TournamentStatistics;
}

/** The catalog key of each Highlight metric's label. */
const METRIC_KEYS = {
    SUCCESSFUL_TICHU: 'statistics.metric.SUCCESSFUL_TICHU',
    BOMBS: 'statistics.metric.BOMBS',
    DOUBLE_WINS: 'statistics.metric.DOUBLE_WINS',
} as const satisfies Record<Highlight['metric'], TranslationKey>;

/**
 * The presentational statistics of one tournament (glossary: Statistics): the Highlights,
 * the per-team table and the Team Trend. It renders the statistics it is given and fetches
 * nothing, so the organizer's and the public tab share one markup. Figures are per
 * team only, never per player.
 */
export function StatisticsView({ statistics }: StatisticsViewProps) {
    const { t, locale } = useI18n();
    const highlightsId = useId();
    const trendId = useId();
    const { teams, highlights, trends } = statistics;

    const decimal = new Intl.NumberFormat(locale, {
        minimumFractionDigits: 1,
        maximumFractionDigits: 1,
    });

    // Every round any team completed, ascending: one chart row and one table row each.
    const roundNumbers = [
        ...new Set(trends.flatMap((trend) => trend.points.map((point) => point.roundNumber))),
    ].sort((a, b) => a - b);
    const hasTrend = roundNumbers.length > 0;
    const chartData = roundNumbers.map((roundNumber) => {
        const row: Record<string, number | null> = { roundNumber };
        for (const trend of trends) {
            const point = trend.points.find((p) => p.roundNumber === roundNumber);
            row[trend.teamId] = point?.victoryPoints ?? null;
        }
        return row;
    });
    const plottedTrends = trends.filter((trend) => trend.points.length > 0);

    return (
        <div className="flex flex-col gap-6">
            {highlights.length > 0 ? (
                <section aria-labelledby={highlightsId} className="flex flex-col gap-3">
                    <h2 id={highlightsId} className="text-base font-medium">
                        {t('statistics.highlights')}
                    </h2>
                    <ul className="grid gap-3 sm:grid-cols-3">
                        {highlights.map((highlight) => (
                            <li
                                key={highlight.metric}
                                className="flex flex-col gap-1 rounded-md border border-border bg-card p-3"
                            >
                                <span className="text-sm text-muted-foreground">
                                    {t(METRIC_KEYS[highlight.metric])}
                                </span>
                                <span className="text-2xl font-semibold tabular-nums">
                                    {highlight.value}
                                </span>
                                <span className="text-sm font-medium">
                                    {highlight.teams.map((team) => team.teamName).join(', ')}
                                </span>
                            </li>
                        ))}
                    </ul>
                </section>
            ) : null}

            <div className="overflow-x-auto rounded-md border border-border bg-card">
                <table className="w-full text-sm tabular-nums">
                    <caption className="sr-only">{t('statistics.title')}</caption>
                    <thead>
                        <tr className="border-b border-border text-left text-muted-foreground">
                            <th scope="col" className="px-3 py-2 font-medium">
                                {t('statistics.team')}
                            </th>
                            <th scope="col" className="px-3 py-2 text-right font-medium">
                                {t('statistics.smallTichu')}
                            </th>
                            <th scope="col" className="px-3 py-2 text-right font-medium">
                                {t('statistics.grandTichu')}
                            </th>
                            <th scope="col" className="px-3 py-2 text-right font-medium">
                                {t('statistics.bombs')}
                            </th>
                            <th scope="col" className="px-3 py-2 text-right font-medium">
                                {t('statistics.bombsPerGame')}
                            </th>
                            <th scope="col" className="px-3 py-2 text-right font-medium">
                                {t('statistics.doubleWins')}
                            </th>
                        </tr>
                    </thead>
                    <tbody className="divide-y divide-border">
                        {teams.map((team) => (
                            <tr key={team.teamId}>
                                <th scope="row" className="px-3 py-2 text-left font-medium">
                                    {team.teamName}
                                </th>
                                <td className="px-3 py-2 text-right">
                                    {team.successfulSmallTichu}
                                </td>
                                <td className="px-3 py-2 text-right">
                                    {team.successfulGrandTichu}
                                </td>
                                <td className="px-3 py-2 text-right">{team.bombCount}</td>
                                <td className="px-3 py-2 text-right">
                                    {team.bombsPerGame === null
                                        ? t('statistics.noValue')
                                        : decimal.format(team.bombsPerGame)}
                                </td>
                                <td className="px-3 py-2 text-right">{team.doubleWins}</td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>

            <section aria-labelledby={trendId} className="flex flex-col gap-3">
                <h2 id={trendId} className="text-base font-medium">
                    {t('statistics.trend.title')}
                </h2>
                {hasTrend ? (
                    <figure className="flex flex-col gap-2">
                        <figcaption className="text-sm text-muted-foreground">
                            {t('statistics.trend.caption')}
                        </figcaption>
                        <div aria-hidden="true" className="h-72 w-full">
                            <ResponsiveContainer width="100%" height="100%">
                                <LineChart data={chartData}>
                                    <CartesianGrid
                                        strokeDasharray="3 3"
                                        stroke="currentColor"
                                        opacity={0.15}
                                    />
                                    <XAxis
                                        dataKey="roundNumber"
                                        type="number"
                                        domain={['dataMin', 'dataMax']}
                                        allowDecimals={false}
                                        label={{
                                            value: t('statistics.trend.round'),
                                            position: 'insideBottom',
                                            offset: -4,
                                        }}
                                    />
                                    <YAxis
                                        allowDecimals={false}
                                        label={{
                                            value: t('statistics.trend.victoryPoints'),
                                            angle: -90,
                                            position: 'insideLeft',
                                        }}
                                    />
                                    <Tooltip />
                                    <Legend />
                                    {plottedTrends.map((trend) => (
                                        <Line
                                            key={trend.teamId}
                                            dataKey={trend.teamId}
                                            name={trend.teamName}
                                            stroke={trendColor(trends.indexOf(trend))}
                                            strokeWidth={2}
                                            dot={false}
                                            isAnimationActive={false}
                                        />
                                    ))}
                                </LineChart>
                            </ResponsiveContainer>
                        </div>
                        <table className="sr-only">
                            <caption>{t('statistics.trend.caption')}</caption>
                            <thead>
                                <tr>
                                    <th scope="col">{t('statistics.trend.round')}</th>
                                    {trends.map((trend) => (
                                        <th scope="col" key={trend.teamId}>
                                            {trend.teamName}
                                        </th>
                                    ))}
                                </tr>
                            </thead>
                            <tbody>
                                {roundNumbers.map((roundNumber) => (
                                    <tr key={roundNumber}>
                                        <th scope="row">{roundNumber}</th>
                                        {trends.map((trend) => {
                                            const point = trend.points.find(
                                                (p) => p.roundNumber === roundNumber,
                                            );
                                            return (
                                                <td key={trend.teamId}>
                                                    {point
                                                        ? point.victoryPoints
                                                        : t('statistics.noValue')}
                                                </td>
                                            );
                                        })}
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </figure>
                ) : (
                    <p className="text-sm text-muted-foreground">{t('statistics.trend.empty')}</p>
                )}
            </section>
        </div>
    );
}
