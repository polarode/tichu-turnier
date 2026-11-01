import { useState } from 'react';
import { CircleCheck } from 'lucide-react';
import { cn } from '@tichu/ui/lib/utils.ts';
import { TabList, TabPanel } from '@tichu/ui/tabs.tsx';

import type { RecordedGame, TeamTable } from '@/domain/game.domain.ts';
import { GameEntryForm } from '@/features/scoring/components/game-entry-form.component.tsx';
import { useTableGames } from '@/features/scoring/hooks/scoring-queries.hook.ts';
import { LoadingIndicator } from '@/shared/components/loading-indicator.component.tsx';
import { PageHeader } from '@/shared/components/page-header.component.tsx';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';

/** A normal (two-team) table hosts four games per round. Trio (1–6) lands with #32. */
const GAME_NUMBERS = [1, 2, 3, 4] as const;

/**
 * The team scoring screen (S5c, #101): a selector of the round's four games with
 * their entered/not-entered state, and the entry grid for the selected game.
 * Games may be entered in any order. Editing an entered game loads it (with its
 * version); a locked round renders every game read-only. A successful save is
 * confirmed in a status line below the form until the next save or game switch.
 */
export function ScoringPage({ table }: { table: TeamTable }) {
    const { t } = useI18n();
    const gamesQuery = useTableGames(table);
    const [selected, setSelected] = useState<number>(1);
    // Lives here, not in the form: a successful save bumps the game's version,
    // which remounts the form and would drop any state it held.
    const [saved, setSaved] = useState<{ gameNumber: number; kind: 'created' | 'updated' } | null>(null);

    if (gamesQuery.isLoading) return <LoadingIndicator />;

    const games = gamesQuery.data ?? [];
    const byNumber = new Map<number, RecordedGame>(games.map((g) => [g.gameNumber, g]));
    const existingGame = byNumber.get(selected);

    const matchup = `${table.teams[0].teamName} ${t('scoring.matchup.versus')} ${table.teams[1].teamName}`;

    return (
        <section className="flex w-full max-w-xl flex-col gap-4">
            <PageHeader titleKey="scoring.title" descriptionKey="scoring.description" />
            <p className="text-sm text-muted-foreground">{matchup}</p>

            <TabList
                id="game"
                label={t('scoring.selector.label')}
                size="touch"
                value={String(selected)}
                onValueChange={(value) => {
                    setSelected(Number(value));
                    setSaved(null);
                }}
                items={GAME_NUMBERS.map((n) => ({
                    value: String(n),
                    done: byNumber.has(n),
                    label: (
                        <>
                            {/* Phones show just the number; the word stays for screen readers. */}
                            <span className="sr-only sm:not-sr-only">{t('scoring.game.label')}</span>{' '}
                            {n}
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

            <TabPanel id="game" value={String(selected)}>
                <GameEntryForm
                    // Remount on game switch / existence change so the grid state resets cleanly.
                    key={`${selected}-${existingGame?.version ?? 'new'}`}
                    table={table}
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
