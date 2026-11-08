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

import type { TeamTrend as TeamTrendData } from '@/domain/statistics.domain.ts';
import { trendColor } from '@/shared/constants/trend-palette.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';

/**
 * The Team Trend (glossary: Statistics): each team's cumulative Victory Points after
 * every completed round, as a line chart with an equivalent screen-reader table. Shown
 * at the end of the organizer's statistics and under the public Final Ranking.
 */
export function TeamTrend({ trends }: { trends: readonly TeamTrendData[] }) {
    const { t } = useI18n();
    const trendId = useId();
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
                    {/* Firefox does not clip a table's caption with sr-only on the table itself. */}
                    <div className="sr-only">
                        <table>
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
                    </div>
                </figure>
            ) : (
                <p className="text-sm text-muted-foreground">{t('statistics.trend.empty')}</p>
            )}
        </section>
    );
}
