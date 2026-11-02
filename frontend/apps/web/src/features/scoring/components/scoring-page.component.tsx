import { useState } from 'react';
import { CircleCheck, Info, Pause } from 'lucide-react';
import { cn } from '@tichu/ui/lib/utils.ts';
import { TabList, TabPanel } from '@tichu/ui/tabs.tsx';

import { matchupOf, type RecordedGame, sitsOut, type TeamTable } from '@/domain/game.domain.ts';
import { GameEntryForm } from '@/features/scoring/components/game-entry-form.component.tsx';
import { useTableGames } from '@/features/scoring/hooks/scoring-queries.hook.ts';
import { LoadingIndicator } from '@/shared/components/loading-indicator.component.tsx';
import { PageHeader } from '@/shared/components/page-header.component.tsx';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';

/**
 * The team scoring screen (S5c, #101; trio tables #110): a selector of the table's
 * games (4, or 6 at a trio table, from the server schedule) with their entered /
 * not-entered state, and the entry grid for the selected game's matchup. Games may
 * be entered in any order; the screen opens on the first one not yet entered. At a
 * trio table the games [viewerTeamId] sits out are marked but stay enterable.
 * Editing an entered game loads it (with its version); a locked round renders every
 * game read-only. A successful save is confirmed in a status line below the form
 * until the next save or game switch.
 */
export function ScoringPage({ table, viewerTeamId }: { table: TeamTable; viewerTeamId?: string }) {
    const { t } = useI18n();
    const gamesQuery = useTableGames(table);
    // Null until the viewer picks a game: the default follows the entered games.
    const [picked, setPicked] = useState<number | null>(null);
    // Lives here, not in the form: a successful save bumps the game's version,
    // which remounts the form and would drop any state it held.
    const [saved, setSaved] = useState<{ gameNumber: number; kind: 'created' | 'updated' } | null>(null);

    if (gamesQuery.isLoading) return <LoadingIndicator />;

    const games = gamesQuery.data ?? [];
    const byNumber = new Map<number, RecordedGame>(games.map((g) => [g.gameNumber, g]));
    const gameNumbers = table.schedule.map((game) => game.gameNumber);
    const selected = picked ?? gameNumbers.find((n) => !byNumber.has(n)) ?? gameNumbers[0] ?? 1;
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
                    // Remount on game switch / existence change so the grid state resets cleanly.
                    key={`${selected}-${existingGame?.version ?? 'new'}`}
                    table={table}
                    matchup={matchup}
                    gameNumber={selected}
                    existingGame={existingGame}
                    onLoadExisting={() => void gamesQuery.refetch()}
                    onReloadRequested={() => void gamesQuery.refetch()}
                    onSubmitStart={() => setSaved(null)}
                    onSaved={(kind) => setSaved({ gameNumber: selected, kind })}
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
