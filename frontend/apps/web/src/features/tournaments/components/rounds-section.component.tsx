import { Fragment, useId, useState } from 'react';
import { ChevronRight } from 'lucide-react';
import { Badge } from '@tichu/ui/badge.tsx';
import { cn } from '@tichu/ui/lib/utils.ts';
import { TabList, TabPanel } from '@tichu/ui/tabs.tsx';

import { isCompleted } from '@/domain/game.domain.ts';
import type { Round, TableOverview } from '@/domain/round.domain.ts';
import type { Team } from '@/domain/team.domain.ts';
import type { ScoringBonuses } from '@/domain/tournament.domain.ts';
import {
    GameResults,
    type GameTileAction,
} from '@/features/scoring/components/game-results.component.tsx';
import { TABLE_STATUS_LABEL_KEYS, tableHeading } from '@/features/scoring/labels.ts';
import { correctionTable } from '@/features/tournaments/correction-table.mapper.ts';
import { GameCorrectionDialog } from '@/features/tournaments/components/game-correction-dialog.component.tsx';
import { useRounds } from '@/features/tournaments/hooks/start-rounds-queries.hook.ts';
import { LoadingIndicator } from '@/shared/components/loading-indicator.component.tsx';
import { TeamDot } from '@/shared/components/team-dot.component.tsx';
import { ConfirmDialog } from '@/shared/components/confirm-dialog.component.tsx';
import { seatTone } from '@/shared/constants/seat-tones.ts';
import { fillPlaceholders } from '@/shared/i18n/fill-placeholders.ts';
import { localizedErrorMessage } from '@/shared/forms/server-error.ts';
import { type I18n, useI18n } from '@/shared/i18n/i18n.context.tsx';
import { TableStatus } from '@/vocabulary/table-status.enum.ts';
import { TournamentStatus } from '@/vocabulary/tournament-status.enum.ts';

interface RoundsSectionProps {
    tournamentId: string;
    /** Team names for the id → name resolution of the table assignments. */
    teams: readonly Team[];
    /** Decides whether a completed table's games can be corrected (ADR 0010). */
    tournamentStatus: TournamentStatus;
    /** The tournament's bonus settings, which the correction's live totals need. */
    bonuses: ScoringBonuses;
}

/**
 * The rounds view. One round shows at a time, picked from a selector that opens on
 * the latest round and follows new rounds until the admin picks one. Each table
 * collapses to a summary line, "Table C: Falcons ✓ vs Owls · status"; all its
 * games (S5d, #102; trio tables #110) unfold behind that line as game tiles
 * with the teams' Victory Points, read-only — scoring and confirming happen in the
 * team area; a round is complete once every table is (glossary: Table Status). Team
 * names resolve from the teams query; a team that has since disappeared falls
 * back to its raw id so the pairing stays legible.
 */
export function RoundsSection({
    tournamentId,
    teams,
    tournamentStatus,
    bonuses,
}: RoundsSectionProps) {
    const { t } = useI18n();
    const roundsQuery = useRounds(tournamentId);
    // null = follow the latest round; a number pins the admin's own choice.
    const [pinnedRound, setPinnedRound] = useState<number | null>(null);
    // A completed table's games are correctable while the tournament is running or finished (ADR 0010).
    const correctable =
        tournamentStatus === TournamentStatus.ACTIVE ||
        tournamentStatus === TournamentStatus.COMPLETED;
    // The game correction in progress (ADR 0010): the organizer confirms it, then edits the game.
    const [correction, setCorrection] = useState<CorrectionTarget | null>(null);

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
        // The correction's table and game as the latest rounds show them; absent once gone.
        const correctedTable =
            correction && findTable(rounds, correction.roundNumber, correction.tableNumber);
        const correctedTeamTable =
            correction && correctedTable
                ? correctionTable({
                      tournamentId,
                      roundNumber: correction.roundNumber,
                      table: correctedTable,
                      teams,
                      bonuses,
                  })
                : undefined;

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
                                            <span className="sr-only">
                                                {t('rounds.round.complete')}
                                            </span>
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
                    onCorrectGame={
                        correctable
                            ? (tableNumber, gameNumber) =>
                                  setCorrection({
                                      stage: 'confirm',
                                      roundNumber: round.number,
                                      tableNumber,
                                      gameNumber,
                                  })
                            : undefined
                    }
                />
                {correction?.stage === 'edit' && correctedTable && correctedTeamTable ? (
                    <GameCorrectionDialog
                        table={correctedTeamTable}
                        gameNumber={correction.gameNumber}
                        game={correctedTable.games.find(
                            (game) => game.gameNumber === correction.gameNumber,
                        )}
                        onReload={async () =>
                            findTable(
                                (await roundsQuery.refetch()).data ?? [],
                                correction.roundNumber,
                                correction.tableNumber,
                            )?.games.find((game) => game.gameNumber === correction.gameNumber)
                        }
                        onClose={() => setCorrection(null)}
                    />
                ) : null}
                {correction?.stage === 'confirm' ? (
                    <ConfirmDialog
                        titleKey="correction.confirm.title"
                        description={fillPlaceholders(t('correction.confirm.description'), {
                            game: String(correction.gameNumber),
                        })}
                        confirmLabelKey="correction.confirm.action"
                        destructive={false}
                        disabled={false}
                        onConfirm={() => setCorrection({ ...correction, stage: 'edit' })}
                        onCancel={() => setCorrection(null)}
                    />
                ) : null}
            </>
        );
    })();

    return (
        <section aria-label={t('rounds.section.title')} className="flex flex-col gap-3">
            {body}
        </section>
    );
}

/** A game correction in progress: the organizer confirms it, then edits the game (ADR 0010). */
interface CorrectionTarget {
    stage: 'confirm' | 'edit';
    roundNumber: number;
    tableNumber: number;
    gameNumber: number;
}

/** The table [tableNumber] of round [roundNumber], while the rounds still list it. */
function findTable(
    rounds: readonly Round[],
    roundNumber: number,
    tableNumber: number,
): TableOverview | undefined {
    return rounds
        .find((r) => r.number === roundNumber)
        ?.tables.find((table) => table.tableNumber === tableNumber);
}

function isRoundComplete(round: Round): boolean {
    return round.tables.every((table) => isCompleted(table.confirmation));
}

/** One round's tables; inside the round selector's tab panel when there is more than one round. */
function RoundView({
    round,
    teamName,
    panelled,
    onCorrectGame,
}: {
    round: Round;
    teamName: (id: string) => string;
    panelled: boolean;
    /** Set when the tournament allows corrections: a game of a completed table is then a button. */
    onCorrectGame?: (tableNumber: number, gameNumber: number) => void;
}) {
    const { t } = useI18n();
    const content = (
        <div className="flex flex-col gap-2 rounded-md border border-border bg-card p-4">
            <h2 className="font-medium">{`${t('rounds.round')} ${round.number}`}</h2>
            <ul className="flex flex-col divide-y divide-border">
                {round.tables.map((table) => (
                    <TableRow
                        key={table.tableNumber}
                        table={table}
                        teamName={teamName}
                        onCorrectGame={
                            onCorrectGame &&
                            ((gameNumber) => onCorrectGame(table.tableNumber, gameNumber))
                        }
                    />
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
 * A table's summary line, a disclosure button that unfolds the table's results:
 * every game (4, or 6 at the trio table) with each team's points or missing status.
 * Each team keeps its seat colour and a ✓ once it confirmed; the trio table
 * carries a "Trio" badge. The chip shows how far the table is (S6c, #107): the
 * entry progress while Playing, then the status with the confirmed count.
 */
function TableRow({
    table,
    teamName,
    onCorrectGame,
}: {
    table: TableOverview;
    teamName: (id: string) => string;
    /** Makes the games clickable; only a completed table's games can be corrected (ADR 0010). */
    onCorrectGame?: (gameNumber: number) => void;
}) {
    const { t } = useI18n();
    const [open, setOpen] = useState(false);
    const gamesId = useId();
    const { confirmedTeamIds } = table.confirmation;
    const chip = tableChip(table, t);
    const gameAction: GameTileAction | undefined =
        onCorrectGame && isCompleted(table.confirmation)
            ? {
                  kind: 'button',
                  onSelect: onCorrectGame,
                  label: (gameNumber) =>
                      fillPlaceholders(t('correction.game.label'), { game: String(gameNumber) }),
              }
            : undefined;

    return (
        <li className="flex flex-col gap-1 py-2 text-sm">
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
                <span className="min-w-0 flex-1">
                    <span className="font-medium">{tableHeading(table.tableNumber, t)}</span>
                    {': '}
                    {table.teamIds.map((teamId, seat) => (
                        <Fragment key={teamId}>
                            {seat > 0 ? ` ${t('rounds.versus')} ` : null}
                            <TeamDot tone={seatTone(seat).dot} />
                            {teamName(teamId)}
                            {confirmedTeamIds.includes(teamId) ? (
                                <>
                                    {' '}
                                    <span aria-hidden="true">✓</span>{' '}
                                    <span className="sr-only">
                                        {t('confirmation.teamConfirmed')}
                                    </span>
                                </>
                            ) : null}
                        </Fragment>
                    ))}
                </span>
                {/* Stacked so a trio table's two badges don't squeeze the team names. */}
                <span className="flex shrink-0 flex-col items-end gap-1">
                    {table.trio ? <Badge variant="secondary">{t('rounds.trio')}</Badge> : null}
                    <span
                        className={cn(
                            'rounded-full px-2 py-0.5 text-xs font-medium tabular-nums',
                            chip.tone,
                        )}
                    >
                        {chip.label}
                    </span>
                </span>
            </button>
            {/* Stays mounted while collapsed so the toggle's aria-controls always resolves. */}
            <div id={gamesId} hidden={!open} className="pb-1 pl-6">
                <GameResults
                    caption={`${t('rounds.games.caption')} – ${tableHeading(table.tableNumber, t)}`}
                    teams={table.teamIds.map((id) => ({ id, name: teamName(id) }))}
                    schedule={table.schedule}
                    games={table.games}
                    gameAction={gameAction}
                />
            </div>
        </li>
    );
}

const DONE_TONE = 'bg-secondary text-secondary-foreground';
const PENDING_TONE = 'bg-warning-soft text-warning';
const UNTOUCHED_TONE = 'bg-muted text-muted-foreground';

/**
 * The table's chip: the entry progress while Playing (grey untouched, amber once
 * games come in), "k/n confirmed" while Confirming (amber),
 * and "Completed" in the done tint — the only state the round's ✓ counts.
 */
function tableChip(table: TableOverview, t: I18n['t']): { label: string; tone: string } {
    const { status } = table.confirmation;
    const statusLabel = t(TABLE_STATUS_LABEL_KEYS[status]);
    switch (status) {
        case TableStatus.COMPLETED:
            return { label: statusLabel, tone: DONE_TONE };
        case TableStatus.CONFIRMING:
            return {
                label: `${table.confirmation.confirmedTeamIds.length}/${table.teamIds.length} ${t('confirmation.count.confirmed')}`,
                tone: PENDING_TONE,
            };
        case TableStatus.PLAYING:
            return {
                label: `${table.games.length}/${table.gameCount} ${t('rounds.games.entered')}`,
                tone: table.games.length > 0 ? PENDING_TONE : UNTOUCHED_TONE,
            };
    }
}
