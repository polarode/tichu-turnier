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
import { GameResults } from '@/features/scoring/components/game-results.component.tsx';
import {
    useConfirmTable,
    useCurrentTable,
    useRetractTable,
    useTableGames,
} from '@/features/scoring/hooks/scoring-queries.hook.ts';
import {
    confirmedCountLabel,
    TABLE_STATUS_LABEL_KEYS,
    tableHeading,
} from '@/features/scoring/labels.ts';
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
 *
 * [readOnly] (S7c, #116) is set once the tournament is no longer ACTIVE: the
 * panel keeps its summary and the scoring link (the screen is read-only there),
 * but offers no Confirm and no Retract.
 */
export function TableConfirmationPanel({
    viewerTeamId,
    readOnly = false,
}: {
    viewerTeamId: string;
    readOnly?: boolean;
}) {
    const tableQuery = useCurrentTable();
    if (tableQuery.isPending) return null;
    if (!tableQuery.data) return <ScoringLink primary={!readOnly} readOnly={readOnly} />;
    return (
        <ConfirmationPanel
            table={tableQuery.data}
            viewerTeamId={viewerTeamId}
            readOnly={readOnly}
        />
    );
}

/**
 * The entry point to the scoring screen where no game tiles offer one: while the
 * team is not seated yet, and beside the Final Ranking. Read-only (S7c, #116) it
 * reads "View table" instead and is never primary.
 */
export function ScoringLink({
    primary,
    readOnly = false,
}: {
    primary: boolean;
    readOnly?: boolean;
}) {
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
            {t(readOnly ? 'scoring.viewTable' : 'teamOverview.scoringLink')}
        </Link>
    );
}

/**
 * A read-only summary of the table's games, its status with who confirmed, and
 * the one control the state allows. Each game tile links to that game on the
 * scoring screen (for entry, or read-only once the table froze or the
 * tournament ended), so the panel needs no separate entry link. Until every
 * game is entered the tiles are all it shows: status and controls follow once
 * the table is complete (a team can only confirm a complete table).
 * - Playing, or Confirming by another team: Confirm, behind a dialog.
 *   Confirming by another team also shows that the games are frozen.
 * - Confirming by this team: the waiting note and Retract (no dialog).
 * - Completed: final, no controls.
 * - Read-only (the tournament is no longer ACTIVE, S7c): the summary only; the
 *   Confirm and Retract controls are not rendered at all.
 *
 * A confirm carries the games exactly as the summary shows them; a rejection
 * (stale games, table changed meanwhile) is explained here, and the table and
 * games reload either way.
 */
function ConfirmationPanel({
    table,
    viewerTeamId,
    readOnly,
}: {
    table: TeamTable;
    viewerTeamId: string;
    readOnly: boolean;
}) {
    const { t } = useI18n();
    const gamesQuery = useTableGames(table);
    const confirm = useConfirmTable(table);
    const retract = useRetractTable(table);
    const [dialogOpen, setDialogOpen] = useState(false);
    const [error, setError] = useState<string | undefined>();

    const { status, confirmedTeamIds } = table.confirmation;
    const games = gamesQuery.data;
    const byNumber = new Map<number, RecordedGame>(
        (games ?? []).map((game) => [game.gameNumber, game]),
    );
    const allEntered =
        games !== undefined && table.schedule.every((game) => byNumber.has(game.gameNumber));
    const viewerConfirmed = confirmedTeamIds.includes(viewerTeamId);

    const teamName = (teamId: string) => teamNameOf(table, teamId);
    const heading = `${t('rounds.round')} ${table.roundNumber} · ${tableHeading(table.tableNumber, t)}`;

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
        <section
            aria-label={heading}
            className="flex flex-col gap-3 rounded-md border border-border bg-card p-4 text-sm"
        >
            <h2 className="font-semibold">{heading}</h2>
            {games ? (
                <GameResults
                    caption={t('confirmation.games.label')}
                    teams={table.teams.map((team) => ({ id: team.teamId, name: team.teamName }))}
                    schedule={table.schedule}
                    games={games}
                    // A tile opens its game, for entry or (frozen, read-only) to view its details.
                    gameAction={{
                        kind: 'link',
                        to: '/team/score',
                        readOnly: readOnly || isFrozen(table.confirmation),
                    }}
                />
            ) : null}
            {/* The tiles show which games are still open; the confirmation step
                only appears once every game is in. */}
            {allEntered ? (
                <>
                    <p>
                        {`${t('confirmation.status.label')}: ${t(TABLE_STATUS_LABEL_KEYS[status])} · ${confirmedCountLabel(table.confirmation, table.teams.length, t)}`}
                    </p>
                    {confirmedTeamIds.length > 0 ? (
                        <p className="text-muted-foreground">
                            {`${t('confirmation.confirmedBy')}: ${confirmedTeamIds.map(teamName).join(', ')}`}
                        </p>
                    ) : null}
                </>
            ) : null}

            <FormAlert message={error} />

            {!allEntered ? null : isCompleted(table.confirmation) ? (
                <p className="text-muted-foreground">{t('confirmation.final')}</p>
            ) : readOnly ? null : viewerConfirmed ? (
                <>
                    <p className="text-muted-foreground">
                        {t(
                            table.teams.length > 2
                                ? 'confirmation.waiting.many'
                                : 'confirmation.waiting.one',
                        )}
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
                        disabled={confirm.isPending}
                        onClick={() => setDialogOpen(true)}
                    >
                        {t('confirmation.confirm')}
                    </Button>
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
