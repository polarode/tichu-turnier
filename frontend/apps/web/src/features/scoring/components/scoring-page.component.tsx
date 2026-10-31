import { useState } from 'react';
import { Button } from '@tichu/ui/button.tsx';

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
 * version); a locked round renders every game read-only.
 */
export function ScoringPage({ table }: { table: TeamTable }) {
    const { t } = useI18n();
    const gamesQuery = useTableGames(table);
    const [selected, setSelected] = useState<number>(1);

    if (gamesQuery.isLoading) return <LoadingIndicator />;

    const games = gamesQuery.data ?? [];
    const byNumber = new Map<number, RecordedGame>(games.map((g) => [g.gameNumber, g]));
    const existingGame = byNumber.get(selected);

    const matchup = `${table.teams[0].teamName} ${t('scoring.matchup.versus')} ${table.teams[1].teamName}`;

    return (
        <section className="flex max-w-xl flex-col gap-4">
            <PageHeader titleKey="scoring.title" descriptionKey="scoring.description" />
            <p className="text-sm text-muted-foreground">{matchup}</p>

            <div role="tablist" aria-label={t('scoring.selector.label')} className="flex gap-2">
                {GAME_NUMBERS.map((n) => {
                    const entered = byNumber.has(n);
                    return (
                        <Button
                            key={n}
                            type="button"
                            role="tab"
                            aria-selected={selected === n}
                            variant={selected === n ? 'default' : entered ? 'secondary' : 'outline'}
                            onClick={() => setSelected(n)}
                        >
                            {t('scoring.game.label')} {n}
                            {entered ? ' ✓' : ''}
                        </Button>
                    );
                })}
            </div>

            <GameEntryForm
                // Remount on game switch / existence change so the grid state resets cleanly.
                key={`${selected}-${existingGame?.version ?? 'new'}`}
                table={table}
                gameNumber={selected}
                existingGame={existingGame}
                onLoadExisting={() => void gamesQuery.refetch()}
                onReloadRequested={() => void gamesQuery.refetch()}
            />
        </section>
    );
}
