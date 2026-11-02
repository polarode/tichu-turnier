import { Fragment, useId, useState } from 'react';
import { ChevronRight } from 'lucide-react';
import { Badge } from '@tichu/ui/badge.tsx';
import { cn } from '@tichu/ui/lib/utils.ts';
import { TabList, TabPanel } from '@tichu/ui/tabs.tsx';

import { type RecordedGame, teamPoints } from '@/domain/game.domain.ts';
import type { Round, TableOverview } from '@/domain/round.domain.ts';
import type { Team } from '@/domain/team.domain.ts';
import { useRounds } from '@/features/tournaments/hooks/start-rounds-queries.hook.ts';
import { LoadingIndicator } from '@/shared/components/loading-indicator.component.tsx';
import { localizedErrorMessage } from '@/shared/forms/server-error.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';

interface RoundsSectionProps {
    tournamentId: string;
    /** Team names for the id → name resolution of the table assignments. */
    teams: readonly Team[];
    /** The tournament is ACTIVE: results are coming in, so the rounds poll. */
    live?: boolean;
}

/**
 * The rounds view. One round shows at a time, picked from a selector that opens on
 * the latest round and follows new rounds until the admin picks one. Each table
 * collapses to a summary line, "Table N: Team A vs Team B · x/n entered"; its
 * entered games (S5d, #102) unfold behind that line, read-only — scoring happens
 * in the team area. Team names resolve from the teams query; a team that has
 * since disappeared falls back to its raw id so the pairing stays legible.
 */
export function RoundsSection({ tournamentId, teams, live = false }: RoundsSectionProps) {
    const { t } = useI18n();
    const roundsQuery = useRounds(tournamentId, { live });
    // null = follow the latest round; a number pins the admin's own choice.
    const [pinnedRound, setPinnedRound] = useState<number | null>(null);

    const body = (() => {
        if (roundsQuery.isPending) return <LoadingIndicator />;
        if (roundsQuery.isError) {
            return (
                <p role="alert" className="text-sm text-destructive">
                    {localizedErrorMessage(roundsQuery.error, t)}
                </p>
            );
        }
        const rounds = roundsQuery.data;
        if (rounds.length === 0) {
            return <p className="text-sm text-muted-foreground">{t('rounds.empty')}</p>;
        }

        const latest = rounds[rounds.length - 1]!;
        const round = rounds.find((r) => r.number === pinnedRound) ?? latest;
        const teamName = (id: string) => teams.find((team) => team.id === id)?.name ?? id;

        return (
            <>
                {rounds.length > 1 ? (
                    <TabList
                        id="round"
                        label={t('rounds.selector.label')}
                        // Up to six rounds share the row; more wrap onto the next line.
                        className="grid-flow-row grid-cols-[repeat(auto-fill,minmax(4.5rem,1fr))]"
                        value={String(round.number)}
                        onValueChange={(value) => setPinnedRound(Number(value))}
                        items={rounds.map((r) => ({
                            value: String(r.number),
                            done: isRoundComplete(r),
                            label: (
                                <>
                                    <span className="sr-only">{t('rounds.round')}</span> {r.number}
                                    {isRoundComplete(r) ? (
                                        <>
                                            {' '}
                                            <span aria-hidden="true">✓</span>{' '}
                                            <span className="sr-only">{t('rounds.round.complete')}</span>
                                        </>
                                    ) : null}
                                </>
                            ),
                        }))}
                    />
                ) : null}
                <RoundView
                    // Reset the open tables when switching rounds.
                    key={round.number}
                    round={round}
                    teamName={teamName}
                    panelled={rounds.length > 1}
                />
            </>
        );
    })();

    return (
        <section aria-label={t('rounds.section.title')} className="flex flex-col gap-3">
            {body}
        </section>
    );
}

function isRoundComplete(round: Round): boolean {
    return round.tables.every((table) => table.games.length >= table.gameCount);
}

/** One round's tables; inside the round selector's tab panel when there is more than one round. */
function RoundView({
    round,
    teamName,
    panelled,
}: {
    round: Round;
    teamName: (id: string) => string;
    panelled: boolean;
}) {
    const { t } = useI18n();
    const content = (
        <div className="flex flex-col gap-2 rounded-md border border-border bg-card p-4">
            <h2 className="font-medium">{`${t('rounds.round')} ${round.number}`}</h2>
            <ul className="flex flex-col divide-y divide-border">
                {round.tables.map((table) => (
                    <TableRow key={table.tableNumber} table={table} teamName={teamName} />
                ))}
            </ul>
        </div>
    );
    return panelled ? (
        <TabPanel id="round" value={String(round.number)}>
            {content}
        </TabPanel>
    ) : (
        content
    );
}

/**
 * A table's summary line. With games entered it is a disclosure button that
 * unfolds them; with none there is nothing to show, so it stays plain text.
 */
function TableRow({ table, teamName }: { table: TableOverview; teamName: (id: string) => string }) {
    const { t } = useI18n();
    const [open, setOpen] = useState(false);
    const gamesId = useId();
    const entered = table.games.length;
    // Done in the "done" tint, partly entered in amber, untouched in grey.
    const progressTone =
        entered >= table.gameCount ? 'bg-secondary text-secondary-foreground'
        : entered > 0 ? 'bg-warning-soft text-warning'
        : 'bg-muted text-muted-foreground';

    const summary = (
        <>
            <span className="min-w-0 flex-1">
                <span className="font-medium">{`${t('rounds.table')} ${table.tableNumber}`}</span>
                {': '}
                {table.teamIds.map((teamId, seat) => (
                    <Fragment key={teamId}>
                        {seat > 0 ? ` ${t('rounds.versus')} ` : null}
                        <TeamDot tone={SEAT_TONES[seat] ?? SEAT_TONES[0]} />
                        {teamName(teamId)}
                    </Fragment>
                ))}
            </span>
            <span className={cn('rounded-full px-2 py-0.5 text-xs font-medium tabular-nums', progressTone)}>
                {`${entered}/${table.gameCount} ${t('rounds.games.entered')}`}
            </span>
        </>
    );

    return (
        <li className="flex flex-col gap-1 py-2 text-sm">
            {table.games.length > 0 ? (
                <button
                    type="button"
                    aria-expanded={open}
                    aria-controls={gamesId}
                    onClick={() => setOpen((wasOpen) => !wasOpen)}
                    className="-mx-2 flex min-h-10 flex-wrap items-center gap-x-2 gap-y-1 rounded-md px-2 text-left transition-colors outline-none hover:bg-accent focus-visible:ring-2 focus-visible:ring-ring"
                >
                    <ChevronRight
                        aria-hidden="true"
                        className={cn('size-4 shrink-0 transition-transform', open && 'rotate-90')}
                    />
                    {summary}
                </button>
            ) : (
                <div className="flex min-h-10 flex-wrap items-center gap-x-2 gap-y-1 pl-6">{summary}</div>
            )}
            {table.games.length > 0 ? (
                // Stays mounted while collapsed so the toggle's aria-controls always resolves.
                <ul id={gamesId} hidden={!open} className="flex flex-col gap-1 pb-1 pl-6">
                    {table.games.map((game) => (
                        <GameRow key={game.gameNumber} table={table} game={game} />
                    ))}
                </ul>
            ) : null}
        </li>
    );
}

/** Seat colours: seat 1 = team 1, seat 2 = team 2; the trio table's third seat stays neutral (#110). */
const SEAT_TONES = ['bg-team-1', 'bg-team-2', 'bg-muted-foreground'] as const;

/** Small colour marker tying a team to its seat colour. */
function TeamDot({ tone }: { tone: string }) {
    return <span aria-hidden="true" className={cn('mr-1 inline-block size-2 rounded-full align-middle', tone)} />;
}

/**
 * One entered game: total Tichu Points of the game's two playing teams in seat order,
 * per the server schedule (the stored team order may differ), plus the Beschiss and
 * double-win markers.
 */
function GameRow({ table, game }: { table: TableOverview; game: RecordedGame }) {
    const { t } = useI18n();
    const label = `${t('rounds.game')} ${game.gameNumber}`;
    // A team missing from the outcome shows a dash, never a misleading 0.
    const points = (teamId: string) => teamPoints(game, teamId) ?? '–';
    const playing = table.schedule.find((scheduled) => scheduled.gameNumber === game.gameNumber)?.teamIds;

    return (
        <li aria-label={label} className="flex flex-wrap items-center gap-2">
            <span className="w-16 text-muted-foreground">{label}</span>
            <span className="tabular-nums">{playing ? `${points(playing[0])} : ${points(playing[1])}` : '–'}</span>
            {game.beschiss ? <Badge variant="warning">{t('rounds.game.beschiss')}</Badge> : null}
            {game.outcome.doubleWinTeam !== null ? (
                <Badge variant="secondary">{t('rounds.game.doubleWin')}</Badge>
            ) : null}
        </li>
    );
}
