import { Badge } from '@tichu/ui/badge.tsx';

import {
    type RecordedGame,
    type ScheduledGame,
    teamPoints,
    totalVictoryPoints,
} from '@/domain/game.domain.ts';
import { TeamDot } from '@/shared/components/team-dot.component.tsx';
import { seatTone } from '@/shared/constants/seat-tones.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';

interface GameResultsTableProps {
    /** Already-localized accessible name of the table. */
    caption: string;
    /** The table's teams in seat order; each gets a column in its seat colour. */
    teams: readonly { id: string; name: string }[];
    /** Every game of the table: who plays it (at the trio table, two of the three teams). */
    schedule: readonly ScheduledGame[];
    /** The games entered so far. */
    games: readonly RecordedGame[];
}

/**
 * A table's games, read-only (admin rounds view, team overview): one row per game,
 * one column per team in seat order, so each Tichu Points total sits under its
 * team whatever order the game stored the teams in. At the trio table the team
 * sitting a game out reads "Pause". A last column carries the Beschiss and
 * double-win markers, or "missing" for a game not entered yet. The footer sums
 * each team's Victory Points over the games entered so far.
 */
export function GameResultsTable({ caption, teams, schedule, games }: GameResultsTableProps) {
    const { t } = useI18n();
    const byNumber = new Map(games.map((game) => [game.gameNumber, game]));

    return (
        // Long team names wrap; on the narrowest phones the table scrolls instead of the page.
        <div className="overflow-x-auto">
            <table className="w-full text-sm">
                <caption className="sr-only">{caption}</caption>
                <thead>
                    <tr className="text-xs text-muted-foreground">
                        <th
                            scope="col"
                            className="w-px py-1 pr-2 text-left font-medium whitespace-nowrap"
                        >
                            {t('rounds.game')}
                        </th>
                        {teams.map((team, seat) => (
                            <th
                                key={team.id}
                                scope="col"
                                className="px-2 py-1 text-right align-bottom font-medium break-words text-foreground"
                            >
                                <TeamDot tone={seatTone(seat).dot} />
                                {team.name}
                            </th>
                        ))}
                        <th scope="col" className="py-1 pl-3">
                            <span className="sr-only">{t('rounds.game.notes')}</span>
                        </th>
                    </tr>
                </thead>
                <tbody className="divide-y divide-border">
                    {schedule.map((scheduled) => {
                        const game = byNumber.get(scheduled.gameNumber);
                        return (
                            <tr key={scheduled.gameNumber}>
                                <th
                                    scope="row"
                                    className="py-1.5 pr-2 text-left font-normal text-muted-foreground tabular-nums"
                                >
                                    {scheduled.gameNumber}
                                </th>
                                {teams.map((team) => (
                                    <td
                                        key={team.id}
                                        className="px-2 py-1.5 text-right tabular-nums"
                                    >
                                        {!scheduled.teamIds.includes(team.id) ? (
                                            <span className="text-xs text-muted-foreground">
                                                {t('rounds.game.sitsOut')}
                                            </span>
                                        ) : (
                                            // A team missing from the outcome shows a dash, never a misleading 0.
                                            ((game && teamPoints(game, team.id)) ?? (
                                                <span
                                                    aria-hidden="true"
                                                    className="text-muted-foreground"
                                                >
                                                    –
                                                </span>
                                            ))
                                        )}
                                    </td>
                                ))}
                                <td className="py-1.5 pl-3">
                                    {game ? (
                                        <span className="flex flex-wrap gap-1">
                                            {game.beschiss ? (
                                                <Badge variant="warning">
                                                    {t('rounds.game.beschiss')}
                                                </Badge>
                                            ) : null}
                                            {game.outcome.doubleWinTeam !== null ? (
                                                <Badge variant="secondary">
                                                    {t('rounds.game.doubleWin')}
                                                </Badge>
                                            ) : null}
                                        </span>
                                    ) : (
                                        <span className="text-muted-foreground">
                                            {t('rounds.game.missing')}
                                        </span>
                                    )}
                                </td>
                            </tr>
                        );
                    })}
                </tbody>
                <tfoot className="border-t-2 border-border">
                    <tr>
                        <th
                            scope="row"
                            className="py-1.5 pr-2 text-left font-medium whitespace-nowrap"
                        >
                            {t('rounds.victoryPoints')}
                        </th>
                        {teams.map((team) => (
                            <td
                                key={team.id}
                                className="px-2 py-1.5 text-right font-semibold tabular-nums"
                            >
                                {totalVictoryPoints(games, team.id)}
                            </td>
                        ))}
                        <td className="py-1.5 pl-3" />
                    </tr>
                </tfoot>
            </table>
        </div>
    );
}
