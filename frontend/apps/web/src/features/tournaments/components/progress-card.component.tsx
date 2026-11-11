import { useState } from 'react';
import { Minus, Plus } from 'lucide-react';
import { Button } from '@tichu/ui/button.tsx';

import { isCompleted } from '@/domain/game.domain.ts';
import type { Round } from '@/domain/round.domain.ts';
import { TOTAL_ROUNDS_MAX, type Tournament } from '@/domain/tournament.domain.ts';
import { useStartNextRound } from '@/features/tournaments/hooks/start-rounds-queries.hook.ts';
import {
    useFinishTournament,
    usePublishTournament,
    useSetTotalRounds,
} from '@/features/tournaments/hooks/tournament-queries.hook.ts';
import { ConfirmDialog } from '@/shared/components/confirm-dialog.component.tsx';
import { localizedErrorMessage } from '@/shared/forms/server-error.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';
import { TournamentStatus } from '@/vocabulary/tournament-status.enum.ts';

interface ProgressCardProps {
    tournament: Tournament;
    /** The tournament's rounds so far; the last one is the current round. */
    rounds: readonly Round[];
    /** Called with the new round's number once the next round has started. */
    onRoundStarted: (roundNumber: number) => void;
}

/** The confirmation dialog currently open, if any. */
type Confirmation = 'nextRound' | 'finish' | 'publish';

/**
 * Where a started tournament stands: "Round x of n" and how many of the current
 * round's tables are completed. While ACTIVE the Total Rounds sit in a stepper whose
 * every step saves at once (optimistically, rolled back with the error shown); it
 * cannot go below the rounds already drawn nor above the maximum. Once finished the
 * card is read-only apart from the lifecycle action.
 *
 * The next round starts from here, after a confirmation: disabled while the current
 * round is incomplete (the progress bar shows how many tables are done). In the
 * last round the same slot finishes the tournament (fixing the Final Ranking, S7c), under the same open-tables
 * rule. Once COMPLETED the organizer publishes the results to the teams; PUBLISHED
 * only notes that they are out.
 */
export function ProgressCard({ tournament, rounds, onRoundStarted }: ProgressCardProps) {
    const { t } = useI18n();
    const setTotalRounds = useSetTotalRounds(tournament.id);
    const startNextRound = useStartNextRound(tournament.id);
    const finishTournament = useFinishTournament(tournament.id);
    const publishTournament = usePublishTournament(tournament.id);
    const [confirming, setConfirming] = useState<Confirmation | null>(null);
    const { totalRounds } = tournament.settings;
    const editable = tournament.status === TournamentStatus.ACTIVE;
    const current = rounds[rounds.length - 1];
    const tableCount = current?.tables.length ?? 0;
    const completedCount =
        current?.tables.filter((table) => isCompleted(table.confirmation)).length ?? 0;

    const openTables = tableCount - completedCount;
    const isLastRound = rounds.length >= totalRounds;

    const step = (delta: number) => setTotalRounds.mutate(totalRounds + delta);
    const start = () =>
        startNextRound.mutate(undefined, {
            onSuccess: (round) => onRoundStarted(round.number),
            onSettled: () => setConfirming(null),
        });
    const finish = () =>
        finishTournament.mutate(undefined, { onSettled: () => setConfirming(null) });
    const publish = () =>
        publishTournament.mutate(undefined, { onSettled: () => setConfirming(null) });

    return (
        <section
            aria-label={t('progress.title')}
            className="flex flex-col gap-3 rounded-md border border-border bg-card p-4"
        >
            <div className="flex flex-wrap items-center gap-2 text-lg font-medium">
                <span>{`${t('rounds.round')} ${rounds.length} ${t('progress.of')}`}</span>{' '}
                {editable ? (
                    <span className="inline-flex items-center gap-1">
                        <Button
                            type="button"
                            variant="outline"
                            size="icon"
                            aria-label={t('progress.removeRound')}
                            disabled={totalRounds <= rounds.length || setTotalRounds.isPending}
                            onClick={() => step(-1)}
                        >
                            <Minus aria-hidden="true" />
                        </Button>
                        <span className="min-w-8 text-center tabular-nums">{totalRounds}</span>
                        <Button
                            type="button"
                            variant="outline"
                            size="icon"
                            aria-label={t('progress.addRound')}
                            disabled={totalRounds >= TOTAL_ROUNDS_MAX || setTotalRounds.isPending}
                            onClick={() => step(1)}
                        >
                            <Plus aria-hidden="true" />
                        </Button>
                    </span>
                ) : (
                    <span className="tabular-nums">{totalRounds}</span>
                )}
            </div>
            {setTotalRounds.isError ? (
                <p role="alert" className="text-sm text-destructive">
                    {localizedErrorMessage(setTotalRounds.error, t)}
                </p>
            ) : null}
            <div className="flex flex-col gap-1.5">
                <div
                    role="progressbar"
                    aria-label={t('progress.tables')}
                    aria-valuemin={0}
                    aria-valuemax={tableCount}
                    aria-valuenow={completedCount}
                    className="h-2 overflow-hidden rounded-full bg-muted"
                >
                    <div
                        className="h-full rounded-full bg-primary transition-[width]"
                        style={{
                            width:
                                tableCount === 0 ? '0%' : `${(completedCount / tableCount) * 100}%`,
                        }}
                    />
                </div>
                <p className="text-sm text-muted-foreground tabular-nums">
                    {`${completedCount}/${tableCount} ${t('progress.tablesCompleted')}`}
                </p>
            </div>
            {editable ? (
                isLastRound ? (
                    <div className="flex flex-wrap items-center gap-3">
                        <Button
                            size="sm"
                            disabled={openTables > 0}
                            onClick={() => setConfirming('finish')}
                        >
                            {t('progress.finish.action')}
                        </Button>
                    </div>
                ) : (
                    <div className="flex flex-wrap items-center gap-3">
                        <Button
                            size="sm"
                            disabled={openTables > 0}
                            onClick={() => setConfirming('nextRound')}
                        >
                            {t('progress.nextRound.action')}
                        </Button>
                    </div>
                )
            ) : null}
            {tournament.status === TournamentStatus.COMPLETED ? (
                <div className="flex flex-wrap items-center gap-3">
                    <Button size="sm" onClick={() => setConfirming('publish')}>
                        {t('progress.publish.action')}
                    </Button>
                </div>
            ) : null}
            {tournament.status === TournamentStatus.PUBLISHED ? (
                <p className="text-sm text-muted-foreground">{t('progress.published')}</p>
            ) : null}
            {startNextRound.isError ? (
                <p role="alert" className="text-sm text-destructive">
                    {localizedErrorMessage(startNextRound.error, t)}
                </p>
            ) : null}
            {finishTournament.isError ? (
                <p role="alert" className="text-sm text-destructive">
                    {localizedErrorMessage(finishTournament.error, t)}
                </p>
            ) : null}
            {publishTournament.isError ? (
                <p role="alert" className="text-sm text-destructive">
                    {localizedErrorMessage(publishTournament.error, t)}
                </p>
            ) : null}
            {confirming === 'nextRound' ? (
                <ConfirmDialog
                    titleKey="progress.nextRound.action"
                    descriptionKey="progress.nextRound.confirmDescription"
                    confirmLabelKey="common.confirm"
                    destructive={false}
                    disabled={startNextRound.isPending}
                    onConfirm={start}
                    onCancel={() => setConfirming(null)}
                />
            ) : null}
            {confirming === 'finish' ? (
                <ConfirmDialog
                    titleKey="progress.finish.action"
                    descriptionKey="progress.finish.confirmDescription"
                    confirmLabelKey="common.confirm"
                    destructive={false}
                    disabled={finishTournament.isPending}
                    onConfirm={finish}
                    onCancel={() => setConfirming(null)}
                />
            ) : null}
            {confirming === 'publish' ? (
                <ConfirmDialog
                    titleKey="progress.publish.action"
                    descriptionKey="progress.publish.confirmDescription"
                    confirmLabelKey="common.confirm"
                    destructive={false}
                    disabled={publishTournament.isPending}
                    onConfirm={publish}
                    onCancel={() => setConfirming(null)}
                />
            ) : null}
        </section>
    );
}
