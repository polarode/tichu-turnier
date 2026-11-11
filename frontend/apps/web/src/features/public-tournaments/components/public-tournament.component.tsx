import { useState } from 'react';
import { Badge } from '@tichu/ui/badge.tsx';
import { cn } from '@tichu/ui/lib/utils.ts';
import { TabList, TabPanel } from '@tichu/ui/tabs.tsx';

import type { StandingsEntry } from '@/domain/standings.domain.ts';
import type { PublicRound, PublicTable } from '@/domain/tournament.domain.ts';
import {
    usePublicStatistics,
    usePublicTournament,
} from '@/features/public-tournaments/hooks/public-tournaments.hook.ts';
import { usePublicTournamentLiveStream } from '@/features/public-tournaments/hooks/public-tournament-live-stream.hook.ts';
import { tableHeading } from '@/features/scoring/labels.ts';
import { TournamentStatusBadge } from '@/features/tournaments/components/tournament-status-badge.component.tsx';
import { isNotFound } from '@/shared/api/api-error.ts';
import { BackLink } from '@/shared/components/back-link.component.tsx';
import { StatisticsPanel } from '@/shared/components/statistics-panel.component.tsx';
import { LiveBadge } from '@/shared/components/live-badge.component.tsx';
import { StandingsTable } from '@/shared/components/standings-table.component.tsx';
import { TeamTrendPanel } from '@/shared/components/team-trend-panel.component.tsx';
import { seatTone } from '@/shared/constants/seat-tones.ts';
import { localizedErrorMessage } from '@/shared/forms/server-error.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';
import { TournamentStatus } from '@/vocabulary/tournament-status.enum.ts';

/** The tabs of a published tournament: its ranking (the default), its rounds and its statistics. */
export type PublicTab = 'ranking' | 'rounds' | 'statistics';

interface PublicTournamentPageProps {
    tournamentId: string;
    /** The active tab, kept in the URL by the route. Ignored until the tournament is published. */
    tab: PublicTab;
    onTabChange: (tab: PublicTab) => void;
}

/**
 * The public tournament page: the pairings of one drawn round at a time, picked from a round
 * selector that opens on the newest round. Once the tournament is published it adds each
 * team's table totals (VP and TP) on its table, and the page splits into tabs: the Final
 * Ranking with the Team Trend (the default), the rounds, and the statistics. Never games,
 * outcomes or table status. Anonymous, so no session is needed; a tournament that is not
 * public reads as not found.
 */
export function PublicTournamentPage({
    tournamentId,
    tab,
    onTabChange,
}: PublicTournamentPageProps) {
    // Outside the body, so loading, not-found and error states keep a way back too.
    return (
        <div className="flex flex-col gap-4">
            <BackLink to="/public" labelKey="publicTournament.backLink" />
            <PublicTournamentBody tournamentId={tournamentId} tab={tab} onTabChange={onTabChange} />
        </div>
    );
}

function PublicTournamentBody({ tournamentId, tab, onTabChange }: PublicTournamentPageProps) {
    const { t } = useI18n();
    const tournamentQuery = usePublicTournament(tournamentId);
    const { live, connected } = usePublicTournamentLiveStream(
        tournamentId,
        tournamentQuery.data?.status,
    );
    const published = tournamentQuery.data?.status === TournamentStatus.PUBLISHED;
    // Only fetched while a tab that shows them is open: the Team Trend sits under the ranking.
    const statisticsQuery = usePublicStatistics(
        tournamentId,
        published && (tab === 'ranking' || tab === 'statistics'),
    );

    if (tournamentQuery.isPending) {
        return <p className="text-muted-foreground">{t('common.loading')}</p>;
    }
    if (tournamentQuery.isError) {
        if (isNotFound(tournamentQuery.error)) {
            return (
                <p role="alert" className="text-destructive">
                    {t('publicTournament.notFound')}
                </p>
            );
        }
        return (
            <p role="alert" className="text-sm text-destructive">
                {localizedErrorMessage(tournamentQuery.error, t)}
            </p>
        );
    }

    const tournament = tournamentQuery.data;
    const rounds = <PublicRounds rounds={tournament.rounds} />;

    return (
        <section className="flex flex-col gap-6">
            <div className="flex flex-wrap items-center gap-3">
                <h1 className="text-xl font-semibold sm:text-2xl">{tournament.name}</h1>
                <TournamentStatusBadge status={tournament.status} />
                {live ? <LiveBadge connected={connected} labelKeyPrefix="public.live" /> : null}
            </div>

            {published ? (
                <div className="flex flex-col gap-4">
                    <TabList
                        id="public-tournament-section"
                        variant="underline"
                        label={t('publicTournament.tabs.label')}
                        value={tab}
                        onValueChange={onTabChange}
                        items={[
                            { value: 'ranking', label: t('publicTournament.tabs.ranking') },
                            { value: 'rounds', label: t('publicTournament.tabs.rounds') },
                            { value: 'statistics', label: t('publicTournament.tabs.statistics') },
                        ]}
                    />
                    <TabPanel
                        id="public-tournament-section"
                        value={tab}
                        className="flex flex-col gap-6"
                    >
                        {tab === 'statistics' ? (
                            <section
                                aria-label={t('statistics.title')}
                                className="flex flex-col gap-3"
                            >
                                <StatisticsPanel query={statisticsQuery} />
                            </section>
                        ) : tab === 'rounds' ? (
                            rounds
                        ) : (
                            <>
                                {tournament.finalRanking ? (
                                    <FinalRankingSection entries={tournament.finalRanking} />
                                ) : null}
                                <TeamTrendPanel query={statisticsQuery} />
                            </>
                        )}
                    </TabPanel>
                </div>
            ) : (
                rounds
            )}
        </section>
    );
}

function FinalRankingSection({ entries }: { entries: StandingsEntry[] }) {
    const { t } = useI18n();
    const title = t('standings.finalTitle');

    return (
        <section aria-label={title} className="flex flex-col gap-3">
            <h2 className="text-lg font-medium">{title}</h2>
            <StandingsTable entries={entries} caption={title} />
        </section>
    );
}

/**
 * The drawn rounds, one at a time: a selector (once there is more than one round) that
 * opens on the newest round and follows new ones until the viewer picks a round.
 */
function PublicRounds({ rounds }: { rounds: readonly PublicRound[] }) {
    const { t } = useI18n();
    // null = follow the newest round; a number pins the viewer's own choice.
    const [pinnedRound, setPinnedRound] = useState<number | null>(null);

    if (rounds.length === 0) {
        return <p className="text-muted-foreground">{t('publicTournament.noRounds')}</p>;
    }

    const ascending = [...rounds].sort((a, b) => a.number - b.number);
    const newest = ascending[ascending.length - 1]!;
    const round = ascending.find((r) => r.number === pinnedRound) ?? newest;

    if (ascending.length === 1) return <PublicRoundView round={round} />;
    return (
        <div className="flex flex-col gap-3">
            <TabList
                id="public-round"
                label={t('rounds.selector.label')}
                // Up to six rounds share the row; more wrap onto the next line.
                className="grid-flow-row grid-cols-[repeat(auto-fill,minmax(4.5rem,1fr))]"
                value={String(round.number)}
                onValueChange={(value) => setPinnedRound(Number(value))}
                items={ascending.map((r) => ({
                    value: String(r.number),
                    label: (
                        <>
                            <span className="sr-only">{t('rounds.round')}</span> {r.number}
                        </>
                    ),
                }))}
            />
            <TabPanel id="public-round" value={String(round.number)}>
                <PublicRoundView round={round} selected />
            </TabPanel>
        </div>
    );
}

/** One round's tables, each as a tug of war of its teams; stacked at every width so the bars line up for comparison. */
function PublicRoundView({ round, selected = false }: { round: PublicRound; selected?: boolean }) {
    const { t } = useI18n();
    const label = `${t('rounds.round')} ${round.number}`;

    return (
        <section aria-label={label} className="flex flex-col gap-1">
            {/* Picked through the selector, whose button already shows the round: heading for screen readers only. */}
            <h2 className={cn('text-lg font-medium', selected && 'sr-only')}>{label}</h2>
            <ul className="flex flex-col gap-5 pt-2">
                {round.tables.map((table) => (
                    <PublicTableItem key={table.tableNumber} table={table} />
                ))}
            </ul>
        </section>
    );
}

/**
 * One table as a tug of war: a bar split by the teams' shares of the table's Victory
 * Points, and right under it the teams side by side (a trio's third in the middle), each
 * led by its Victory Points in its seat colour (the leader's in full, the others' faded),
 * then its name, players and Tichu Points. The Victory Points sit next to the bar they
 * size; the Tichu Points stay with their team. Before publishing (or at 0 : 0) the bar
 * splits evenly and faded. The bar is visual only.
 */
function PublicTableItem({ table }: { table: PublicTable }) {
    const { t } = useI18n();
    const heading = tableHeading(table.tableNumber, t);
    const totals = table.teams.map((team) => team.totals);
    const published = totals.every((total) => total !== undefined);
    const victoryPoints = totals.map((total) => total?.victoryPoints ?? 0);
    const sum = victoryPoints.reduce((acc, vp) => acc + vp, 0);
    const best = Math.max(...victoryPoints);
    // A sole leader keeps its colour in full; a tie or a table without results has none.
    const leader =
        published && victoryPoints.filter((vp) => vp === best).length === 1
            ? victoryPoints.indexOf(best)
            : -1;
    const last = table.teams.length - 1;
    // The first team aligns left, the last right, a trio's middle team centred.
    const align = (seat: number) =>
        seat === 0
            ? 'items-start'
            : seat === last
              ? 'items-end text-right'
              : 'items-center text-center';

    return (
        <li className="flex flex-col gap-2">
            <div className="flex items-center gap-2">
                <h3 className="text-xs font-medium tracking-wide text-muted-foreground uppercase">
                    {heading}
                </h3>
                {table.trio ? <Badge variant="secondary">{t('rounds.trio')}</Badge> : null}
            </div>
            <div aria-hidden="true" className="flex h-3 overflow-hidden rounded-full bg-muted">
                {table.teams.map((team, seat) => (
                    <span
                        key={team.id}
                        className={cn(
                            'h-full transition-[width]',
                            seatTone(seat).dot,
                            !(published && sum > 0) && 'opacity-30',
                            seat > 0 && 'border-l-2 border-card',
                        )}
                        style={{
                            width: `${published && sum > 0 ? ((victoryPoints[seat] ?? 0) / sum) * 100 : 100 / table.teams.length}%`,
                        }}
                    />
                ))}
            </div>
            <ul className="flex items-start justify-between gap-3">
                {table.teams.map((team, seat) => (
                    <li key={team.id} className={cn('flex min-w-0 flex-col', align(seat))}>
                        <span className="flex max-w-full items-baseline gap-1.5">
                            {team.totals ? (
                                <span
                                    className={cn(
                                        'shrink-0 text-xl font-bold tabular-nums',
                                        seatTone(seat).text,
                                        leader !== -1 && seat !== leader && 'opacity-60',
                                    )}
                                >
                                    {team.totals.victoryPoints}{' '}
                                    <abbr
                                        title={t('standings.victoryPoints.long')}
                                        className="text-xs font-medium no-underline"
                                    >
                                        {t('standings.victoryPoints')}
                                    </abbr>
                                </span>
                            ) : null}{' '}
                            <span className="truncate text-sm font-semibold">{team.name}</span>
                        </span>
                        <span className="max-w-full truncate text-xs text-muted-foreground">
                            {team.players.map((player) => player.name).join(' · ')}
                        </span>
                        {team.totals ? (
                            <span className="text-xs text-muted-foreground tabular-nums">
                                {`${team.totals.tichuPoints} ${t('standings.tichuPoints')}`}
                            </span>
                        ) : null}
                    </li>
                ))}
            </ul>
        </li>
    );
}
