import { useState } from 'react';
import { Link, useNavigate } from '@tanstack/react-router';
import { CircleCheck, Info } from 'lucide-react';
import { buttonVariants } from '@tichu/ui/button.tsx';

import {
    isCompleted,
    isFrozen,
    isLockedByWithdrawal,
    isSettled,
    matchupOf,
    type RecordedGame,
    sitsOut,
    teamNameOf,
    type TeamTable,
} from '@/domain/game.domain.ts';
import { GameEntryForm } from '@/features/scoring/components/game-entry-form.component.tsx';
import {
    useRecordGame,
    useReloadCurrentTable,
    useTableGames,
} from '@/features/scoring/hooks/scoring-queries.hook.ts';
import { WithdrawalNotice } from '@/features/teams/components/withdrawal-notice.component.tsx';
import { BackLink } from '@/shared/components/back-link.component.tsx';
import { LiveBadge } from '@/shared/components/live-badge.component.tsx';
import { LoadingIndicator } from '@/shared/components/loading-indicator.component.tsx';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';
import { TournamentStatus } from '@/vocabulary/tournament-status.enum.ts';
import type { WithdrawalReason } from '@/vocabulary/withdrawal-reason.enum.ts';

/**
 * The team scoring screen (S5c, #101; trio tables #110): the entry grid for one
 * game's matchup. The team overview's game tiles pick the game: a `?game=` link
 * ([initialGame]) opens that game when it is on the schedule, otherwise the screen
 * opens on the first game not yet entered. At a trio table a game [viewerTeamId]
 * sits out is marked but stays enterable. Editing an entered game loads it (with
 * its version). A successful save leads back to the overview, where the next tile
 * is one tap away. A frozen table (someone confirmed, ADR 0006) renders the game
 * read-only with an explanation of who confirmed and how to edit again. A back
 * link leads to the team overview, and once every game is entered on an open
 * table a prompt points there to confirm. Once the tournament is no longer ACTIVE
 * (`tournamentStatus`, S7c, #116) the screen is read-only and the confirm prompt
 * gives way to a notice; an absent status (overview unavailable) leaves the screen
 * editable, as before. The table state and games follow the team's live stream
 * while `live` (the tournament is not yet published), with `connected` shown as a
 * Live badge. A withdrawn team (ADR 0011, [withdrawalReason]) is locked like a finished
 * tournament and gets a notice; a game against a withdrawn team is never entered, so the
 * next game to enter skips it, and such a game opens read-only.
 */
export function ScoringPage({
    table,
    initialGame,
    viewerTeamId,
    tournamentStatus,
    withdrawalReason,
    live = false,
    connected = false,
}: {
    table: TeamTable;
    /** Game to open on instead of the first unentered one; ignored unless on the schedule. */
    initialGame?: number;
    viewerTeamId?: string;
    tournamentStatus?: TournamentStatus;
    /** Why the viewing team withdrew (ADR 0011); null or absent while it plays on. */
    withdrawalReason?: WithdrawalReason | null;
    /** Whether the live stream applies; only then is the connection state shown. */
    live?: boolean;
    /** Whether the live stream is connected; only read while `live`. */
    connected?: boolean;
}) {
    const { t } = useI18n();
    const navigate = useNavigate();
    const reloadCurrentTable = useReloadCurrentTable();
    const recordGame = useRecordGame(table);
    const gamesQuery = useTableGames(table);
    // Null until the games load: the linked game, else the first game not yet entered.
    const [pinned, setPinned] = useState<number | null>(null);

    if (gamesQuery.isLoading) return <LoadingIndicator />;

    const games = gamesQuery.data ?? [];
    const byNumber = new Map<number, RecordedGame>(games.map((g) => [g.gameNumber, g]));
    const settled = (gameNumber: number) => isSettled(gameNumber, games, table.forfeits);
    // A game against a withdrawn team is never entered: it is a forfeit or only viewed.
    const lockedGame = (gameNumber: number) => {
        const scheduled = table.schedule.find((game) => game.gameNumber === gameNumber);
        return scheduled !== undefined && isLockedByWithdrawal(scheduled, table.withdrawnTeamIds);
    };
    const gameNumbers = table.schedule.map((game) => game.gameNumber);
    const linked =
        initialGame !== undefined && gameNumbers.includes(initialGame) ? initialGame : undefined;
    const selected =
        pinned ??
        linked ??
        gameNumbers.find((n) => !settled(n) && !lockedGame(n)) ??
        gameNumbers[0] ??
        1;
    // Pin the opening game once the games are in, so a poll that fills it does
    // not move the viewer to another game mid-entry.
    if (pinned === null && gamesQuery.isSuccess) setPinned(selected);
    const existingGame = byNumber.get(selected);
    const matchup = matchupOf(table, selected);
    // Confirming happens on the overview, so point there once nothing is left to enter.
    const allEntered = gamesQuery.isSuccess && gameNumbers.every(settled);
    // The tournament left ACTIVE (S7c), or the viewing team withdrew (ADR 0011): nothing
    // here may be written any more.
    const withdrawn = withdrawalReason !== undefined && withdrawalReason !== null;
    const tournamentLocked =
        withdrawn ||
        (tournamentStatus !== undefined && tournamentStatus !== TournamentStatus.ACTIVE);
    // Without the viewer's identity (overview unavailable) no game is marked as sat out.
    const sittingOut = viewerTeamId !== undefined && sitsOut(table, viewerTeamId, selected);

    const versus = ` ${t('scoring.matchup.versus')} `;
    const header = table.trio
        ? `${t('scoring.matchup.trio')}: ${table.teams.map((team) => team.teamName).join(', ')}`
        : table.teams.map((team) => team.teamName).join(versus);

    return (
        <section className="flex w-full max-w-xl flex-col gap-4">
            <BackLink to="/team" labelKey="scoring.overviewLink" />
            {/* Which table and who plays there is all the heading needs to say. The live
                dot follows the title, as on every page that has one. */}
            <div className="flex min-w-0 flex-col gap-1">
                <div className="flex flex-wrap items-center gap-2">
                    <h1 className="text-xl font-semibold sm:text-2xl">
                        {`${t('rounds.table')} ${table.tableNumber}`}
                        <span className="text-base font-normal text-muted-foreground">
                            {` · ${t('rounds.round')} ${table.roundNumber}`}
                        </span>
                    </h1>
                    {live ? <LiveBadge connected={connected} labelKeyPrefix="team.live" /> : null}
                </div>
                <p className="font-medium">{header}</p>
            </div>
            {withdrawalReason ? <WithdrawalNotice reason={withdrawalReason} /> : null}
            {isFrozen(table.confirmation) ? (
                <FrozenNotice table={table} viewerTeamId={viewerTeamId} />
            ) : tournamentLocked && !withdrawn ? (
                <p className="rounded-md bg-muted px-3 py-2 text-sm">
                    {t('scoring.tournamentLocked')}
                </p>
            ) : null}

            <div className="flex flex-col gap-3">
                {/* At the trio table the matchup changes from game to game, so it is named too. */}
                <h2 className="font-semibold">
                    {table.trio
                        ? `${t('scoring.game.label')} ${selected}: ${matchup[0].teamName}${versus}${matchup[1].teamName}`
                        : `${t('scoring.game.label')} ${selected}`}
                </h2>
                {sittingOut ? (
                    <p className="flex items-center gap-2 rounded-md bg-muted px-3 py-2 text-sm text-muted-foreground">
                        <Info aria-hidden="true" className="size-4 shrink-0" />
                        {t('scoring.sitOut.note')}
                    </p>
                ) : null}
                {/* A fresh form per game, and once the games have loaded after a failure, so it
                    reads the right game's draft at mount. */}
                <GameEntryForm
                    key={`${selected}:${gamesQuery.isSuccess}`}
                    table={table}
                    matchup={matchup}
                    gameNumber={selected}
                    save={recordGame.mutateAsync}
                    existingGame={existingGame}
                    onReload={async () =>
                        (await gamesQuery.refetch()).data?.find(
                            (game) => game.gameNumber === selected,
                        )
                    }
                    onSaved={() => void navigate({ to: '/team' })}
                    onFrozen={() => void reloadCurrentTable()}
                    tournamentLocked={tournamentLocked}
                    withdrawnMatchup={lockedGame(selected)}
                    draftsReady={gamesQuery.isSuccess}
                />
            </div>

            {allEntered ? (
                <div className="flex flex-col gap-3 rounded-md bg-success-soft px-3 py-3 text-sm sm:flex-row sm:items-center sm:justify-between">
                    <p className="flex items-center gap-2 font-medium text-success">
                        <CircleCheck aria-hidden="true" className="size-4 shrink-0" />
                        {t('scoring.allEntered')}
                    </p>
                    <Link
                        to="/team"
                        className={buttonVariants({ variant: 'outline', size: 'touch' })}
                    >
                        {t('scoring.overviewLink')}
                    </Link>
                </div>
            ) : null}
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

    return <p className="rounded-md bg-muted px-3 py-2 text-sm">{message}</p>;
}
