import { useState } from 'react';
import { Link } from '@tanstack/react-router';
import { Button, buttonVariants } from '@tichu/ui/button.tsx';

import {
    isCompleted,
    isFrozen,
    type RecordedGame,
    teamNameOf,
    type TeamTable,
} from '@/domain/game.domain.ts';
import { GameResultsTable } from '@/features/scoring/components/game-results-table.component.tsx';
import {
    useConfirmTable,
    useCurrentTable,
    useRetractTable,
    useTableGames,
} from '@/features/scoring/hooks/scoring-queries.hook.ts';
import { confirmedCountLabel, TABLE_STATUS_LABEL_KEYS, tableHeading } from '@/features/scoring/labels.ts';
import { ApiError } from '@/shared/api/api-error.ts';
import { ConfirmDialog } from '@/shared/components/confirm-dialog.component.tsx';
import { FormAlert } from '@/shared/components/form-alert.component.tsx';
import { localizedErrorMessage } from '@/shared/forms/server-error.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';
import { TableStatus } from '@/vocabulary/table-status.enum.ts';

/**
 * The team overview's confirmation panel (S6b, #106; ADR 0006) for the team's
 * current table, holding the entry point to the scoring screen. Nothing shows
 * while the table loads (so the link does not jump into the panel); when the
 * team is not seated (current table 404s) only the entry point shows, and the
 * scoring screen explains the rest.
 */
export function TableConfirmationPanel({ viewerTeamId }: { viewerTeamId: string }) {
    const tableQuery = useCurrentTable();
    if (tableQuery.isPending) return null;
    if (!tableQuery.data) return <ScoringLink primary />;
    return <ConfirmationPanel table={tableQuery.data} viewerTeamId={viewerTeamId} />;
}

/** "Enter results": the primary action until every game is in, then it yields to Confirm. */
function ScoringLink({ primary }: { primary: boolean }) {
    const { t } = useI18n();
    return (
        <Link
            to="/team/score"
            className={buttonVariants({
                variant: primary ? 'default' : 'outline',
                size: 'touch',
                className: 'w-full sm:w-auto sm:self-start',
            })}
        >
            {t('teamOverview.scoringLink')}
        </Link>
    );
}

/**
 * A read-only summary of the table's games, its status with who confirmed, and
 * the one control the state allows:
 * - Playing, or Confirming by another team: Confirm, disabled with an "x of n
 *   games entered" hint until every game is in, behind a dialog. Confirming by
 *   another team also shows that the games are frozen.
 * - Confirming by this team: the waiting note and Retract (no dialog).
 * - Completed: final, no controls.
 *
 * A confirm carries the games exactly as the summary shows them; a rejection
 * (stale games, table changed meanwhile) is explained here, and the table and
 * games reload either way.
 */
function ConfirmationPanel({ table, viewerTeamId }: { table: TeamTable; viewerTeamId: string }) {
    const { t } = useI18n();
    const gamesQuery = useTableGames(table);
    const confirm = useConfirmTable(table);
    const retract = useRetractTable(table);
    const [dialogOpen, setDialogOpen] = useState(false);
    const [error, setError] = useState<string | undefined>();

    const { status, confirmedTeamIds } = table.confirmation;
    const games = gamesQuery.data;
    const byNumber = new Map<number, RecordedGame>((games ?? []).map((game) => [game.gameNumber, game]));
    const gameCount = table.schedule.length;
    const allEntered = games !== undefined && table.schedule.every((game) => byNumber.has(game.gameNumber));
    const viewerConfirmed = confirmedTeamIds.includes(viewerTeamId);

    const teamName = (teamId: string) => teamNameOf(table, teamId);
    const heading = `${t('rounds.round')} ${table.roundNumber} · ${tableHeading(table.tableNumber, t)}`;
    const outOf = ` ${t('confirmation.count.of')} `;

    function explain(cause: unknown) {
        setError(
            cause instanceof ApiError && cause.code === 'GAME_CONCURRENT_MODIFICATION'
                ? t('confirmation.stale')
                : localizedErrorMessage(cause, t),
        );
    }

    function confirmGames() {
        if (!games) return;
        setDialogOpen(false);
        setError(undefined);
        confirm.mutate(games, { onError: explain });
    }

    function retractConfirmation() {
        setError(undefined);
        retract.mutate(undefined, { onError: explain });
    }

    return (
        <section aria-label={heading} className="flex flex-col gap-3 rounded-md border border-border bg-card p-4 text-sm">
            <h2 className="font-semibold">{heading}</h2>
            {games ? (
                <GameResultsTable
                    caption={t('confirmation.games.label')}
                    teams={table.teams.map((team) => ({ id: team.teamId, name: team.teamName }))}
                    schedule={table.schedule}
                    games={games}
                />
            ) : null}
            <ScoringLink primary={!allEntered && !isFrozen(table.confirmation)} />

            <p>
                {`${t('confirmation.status.label')}: ${t(TABLE_STATUS_LABEL_KEYS[status])} · ${confirmedCountLabel(table.confirmation, table.teams.length, t)}`}
            </p>
            {confirmedTeamIds.length > 0 ? (
                <p className="text-muted-foreground">
                    {`${t('confirmation.confirmedBy')}: ${confirmedTeamIds.map(teamName).join(', ')}`}
                </p>
            ) : null}

            <FormAlert message={error} />

            {isCompleted(table.confirmation) ? (
                <p className="text-muted-foreground">{t('confirmation.final')}</p>
            ) : viewerConfirmed ? (
                <>
                    <p className="text-muted-foreground">
                        {t(table.teams.length > 2 ? 'confirmation.waiting.many' : 'confirmation.waiting.one')}
                    </p>
                    <Button
                        variant="outline"
                        size="touch"
                        className="w-full sm:w-auto sm:self-start"
                        disabled={retract.isPending}
                        onClick={retractConfirmation}
                    >
                        {t('confirmation.retract')}
                    </Button>
                </>
            ) : (
                <>
                    {status === TableStatus.CONFIRMING ? (
                        <p className="text-muted-foreground">{t('confirmation.frozen')}</p>
                    ) : null}
                    <Button
                        size="touch"
                        className="w-full sm:w-auto sm:self-start"
                        disabled={!allEntered || confirm.isPending}
                        onClick={() => setDialogOpen(true)}
                    >
                        {t('confirmation.confirm')}
                    </Button>
                    {games && !allEntered ? (
                        <p className="text-muted-foreground">
                            {`${byNumber.size}${outOf}${gameCount} ${t('confirmation.gamesEntered')}`}
                        </p>
                    ) : null}
                </>
            )}

            {dialogOpen ? (
                <ConfirmDialog
                    titleKey="confirmation.dialog.title"
                    descriptionKey="confirmation.dialog.description"
                    confirmLabelKey="confirmation.dialog.confirm"
                    destructive={false}
                    disabled={confirm.isPending}
                    onConfirm={confirmGames}
                    onCancel={() => setDialogOpen(false)}
                />
            ) : null}
        </section>
    );
}
