import { useState } from 'react';
import { Link } from '@tanstack/react-router';
import { CircleCheck, Info, Pause } from 'lucide-react';
import { cn } from '@tichu/ui/lib/utils.ts';
import { TabList, TabPanel } from '@tichu/ui/tabs.tsx';

import {
    isCompleted,
    isFrozen,
    matchupOf,
    type RecordedGame,
    sitsOut,
    teamNameOf,
    type TeamTable,
} from '@/domain/game.domain.ts';
import { GameEntryForm } from '@/features/scoring/components/game-entry-form.component.tsx';
import { useReloadCurrentTable, useTableGames } from '@/features/scoring/hooks/scoring-queries.hook.ts';
import { LoadingIndicator } from '@/shared/components/loading-indicator.component.tsx';
import { PageHeader } from '@/shared/components/page-header.component.tsx';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';

/**
 * The team scoring screen (S5c, #101; trio tables #110): a selector of the table's
 * games (4, or 6 at a trio table, from the server schedule) with their entered /
 * not-entered state, and the entry grid for the selected game's matchup. Games may
 * be entered in any order; the screen opens on the first one not yet entered. At a
 * trio table the games [viewerTeamId] sits out are marked but stay enterable.
 * Editing an entered game loads it (with its version). A frozen table (someone
 * confirmed, ADR 0006) renders every game read-only with an explanation of who
 * confirmed and how to edit again. The table state and games poll until the
 * table is completed (#106). A successful save is confirmed in a status line
 * below the form until the next save or game switch.
 */
export function ScoringPage({ table, viewerTeamId }: { table: TeamTable; viewerTeamId?: string }) {
    const { t } = useI18n();
    const reloadCurrentTable = useReloadCurrentTable();
    const gamesQuery = useTableGames(table);
    // Null until the games load: the default is the first game not yet entered.
    const [picked, setPicked] = useState<number | null>(null);
    // Lives here, not in the form: a successful save bumps the game's version,
    // which remounts the form and would drop any state it held.
    const [saved, setSaved] = useState<{ gameNumber: number; kind: 'created' | 'updated' } | null>(null);

    if (gamesQuery.isLoading) return <LoadingIndicator />;

    const games = gamesQuery.data ?? [];
    const byNumber = new Map<number, RecordedGame>(games.map((g) => [g.gameNumber, g]));
    const gameNumbers = table.schedule.map((game) => game.gameNumber);
    const selected = picked ?? gameNumbers.find((n) => !byNumber.has(n)) ?? gameNumbers[0] ?? 1;
    // Pin the opening game once the games are in, so a poll that fills it does
    // not move the viewer to another game mid-entry.
    if (picked === null && gamesQuery.isSuccess) setPicked(selected);
    const existingGame = byNumber.get(selected);
    const matchup = matchupOf(table, selected);
    // Without the viewer's identity (overview unavailable) no game is marked as sat out.
    const sittingOut = viewerTeamId !== undefined && sitsOut(table, viewerTeamId, selected);

    const versus = ` ${t('scoring.matchup.versus')} `;
    const header = table.trio
        ? `${t('scoring.matchup.trio')}: ${table.teams.map((team) => team.teamName).join(', ')}`
        : table.teams.map((team) => team.teamName).join(versus);

    return (
        <section className="flex w-full max-w-xl flex-col gap-4">
            <PageHeader titleKey="scoring.title" descriptionKey="scoring.description" />
            <p className="text-sm text-muted-foreground">{header}</p>
            {isFrozen(table.confirmation) ? <FrozenNotice table={table} viewerTeamId={viewerTeamId} /> : null}

            <TabList
                id="game"
                label={t('scoring.selector.label')}
                size="touch"
                value={String(selected)}
                onValueChange={(value) => {
                    setPicked(Number(value));
                    setSaved(null);
                }}
                items={gameNumbers.map((n) => ({
                    value: String(n),
                    done: byNumber.has(n),
                    label: (
                        <>
                            {/* Phones show just the number; the word stays for screen readers. */}
                            <span className="sr-only sm:not-sr-only">{t('scoring.game.label')}</span>{' '}
                            {n}
                            {viewerTeamId !== undefined && sitsOut(table, viewerTeamId, n) ? (
                                <>
                                    {' '}
                                    <Pause aria-hidden="true" className="inline size-3 text-muted-foreground" />
                                    <span className="sr-only">{`– ${t('scoring.game.sitsOut')}`}</span>
                                </>
                            ) : null}
                            {byNumber.has(n) ? (
                                <>
                                    {' '}
                                    <span aria-hidden="true">✓</span>{' '}
                                    <span className="sr-only">{t('scoring.game.entered')}</span>
                                </>
                            ) : null}
                        </>
                    ),
                }))}
            />

            <TabPanel id="game" value={String(selected)} className="flex flex-col gap-3">
                {table.trio ? (
                    <p className="text-sm font-medium">
                        {`${t('scoring.game.label')} ${selected}: ${matchup[0].teamName}${versus}${matchup[1].teamName}`}
                    </p>
                ) : null}
                {sittingOut ? (
                    <p className="flex items-center gap-2 rounded-md bg-muted px-3 py-2 text-sm text-muted-foreground">
                        <Info aria-hidden="true" className="size-4 shrink-0" />
                        {t('scoring.sitOut.note')}
                    </p>
                ) : null}
                <GameEntryForm
                    // Remount on game switch only: a newer version is merged by the form itself.
                    key={selected}
                    table={table}
                    matchup={matchup}
                    gameNumber={selected}
                    existingGame={existingGame}
                    onReload={async () =>
                        (await gamesQuery.refetch()).data?.find((game) => game.gameNumber === selected)
                    }
                    onSubmitStart={() => setSaved(null)}
                    onSaved={(kind) => setSaved({ gameNumber: selected, kind })}
                    onFrozen={() => void reloadCurrentTable()}
                />
            </TabPanel>

            {/* Always mounted so screen readers announce the text when it appears. */}
            <p
                role="status"
                className={cn(
                    'flex min-h-5 items-center justify-center gap-2 rounded-md text-sm font-medium',
                    saved && 'bg-success-soft px-3 py-2 text-success',
                )}
            >
                {saved ? (
                    <>
                        <CircleCheck aria-hidden="true" className="size-4" />
                        {`${t('scoring.game.label')} ${saved.gameNumber} ${t(
                            saved.kind === 'created' ? 'scoring.saved.created' : 'scoring.saved.updated',
                        )}`}
                    </>
                ) : null}
            </p>
        </section>
    );
}

/**
 * Why a frozen table's games are read-only: final once completed; while
 * confirming, who confirmed and that the confirming team must retract on its
 * team overview to edit again (addressing the viewer when that is its own team).
 */
function FrozenNotice({ table, viewerTeamId }: { table: TeamTable; viewerTeamId?: string }) {
    const { t } = useI18n();
    const { confirmedTeamIds } = table.confirmation;
    if (isCompleted(table.confirmation)) {
        return <p className="rounded-md bg-muted px-3 py-2 text-sm">{t('scoring.completed')}</p>;
    }

    const names = confirmedTeamIds.map((teamId) => teamNameOf(table, teamId)).join(', ');
    const message =
        viewerTeamId !== undefined && confirmedTeamIds.includes(viewerTeamId)
            ? t('scoring.frozen.byOwn')
            : `${t('confirmation.confirmedBy')}: ${names}. ${t('scoring.frozen.byOthers')}`;

    return (
        <div className="flex flex-col gap-1 rounded-md bg-muted px-3 py-2 text-sm">
            <p>{message}</p>
            <Link to="/team" className="font-medium underline underline-offset-4">
                {t('scoring.frozen.overviewLink')}
            </Link>
        </div>
    );
}
