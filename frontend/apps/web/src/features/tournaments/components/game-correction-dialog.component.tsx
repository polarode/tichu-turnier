import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogHeader,
    DialogTitle,
} from '@tichu/ui/dialog.tsx';

import { matchupOf, type RecordedGame, type TeamTable } from '@/domain/game.domain.ts';
import { GameEntryForm } from '@/features/scoring/components/game-entry-form.component.tsx';
import { useCorrectGame } from '@/features/tournaments/hooks/game-correction-queries.hook.ts';
import { tableHeading } from '@/features/scoring/labels.ts';
import { fillPlaceholders } from '@/shared/i18n/fill-placeholders.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';

/**
 * The organizer's editor for a game at a completed table (ADR 0010): the team's
 * entry grid, prefilled with the game as entered and saved as a correction. A
 * successful save closes it; a rejected one keeps it open with the localized error.
 * [game] is the stored game as the rounds view last loaded it, absent while not
 * entered; [onReload] fetches the latest stored version after a concurrent change.
 */
export function GameCorrectionDialog({
    table,
    gameNumber,
    game,
    onReload,
    onClose,
}: {
    table: TeamTable;
    gameNumber: number;
    game?: RecordedGame;
    onReload: () => Promise<RecordedGame | undefined>;
    onClose: () => void;
}) {
    const { t } = useI18n();
    const correct = useCorrectGame(table.tournamentId);
    const tableRef = {
        tournamentId: table.tournamentId,
        roundNumber: table.roundNumber,
        tableNumber: table.tableNumber,
    };

    return (
        <Dialog open onOpenChange={(open) => (open ? undefined : onClose())}>
            <DialogContent className="sm:max-w-2xl" closeLabel={t('correction.editor.close')}>
                <DialogHeader>
                    <DialogTitle>
                        {fillPlaceholders(t('correction.editor.title'), {
                            game: String(gameNumber),
                            table: tableHeading(table.tableNumber, t),
                        })}
                    </DialogTitle>
                    <DialogDescription>{t('correction.editor.description')}</DialogDescription>
                </DialogHeader>
                <GameEntryForm
                    table={table}
                    matchup={matchupOf(table, gameNumber)}
                    gameNumber={gameNumber}
                    save={(input) => correct.mutateAsync({ table: tableRef, input })}
                    existingGame={game}
                    onReload={onReload}
                    onSaved={onClose}
                    correcting
                />
            </DialogContent>
        </Dialog>
    );
}
