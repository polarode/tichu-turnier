import { useId } from 'react';
import { Link } from '@tanstack/react-router';
import { Badge } from '@tichu/ui/badge.tsx';
import { cn } from '@tichu/ui/lib/utils.ts';

import {
    type RecordedGame,
    type ScheduledGame,
    teamPoints,
    totalVictoryPoints,
} from '@/domain/game.domain.ts';
import { TeamDot } from '@/shared/components/team-dot.component.tsx';
import { seatTone } from '@/shared/constants/seat-tones.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';

type ResultTeam = { id: string; name: string };

/**
 * What a game tile does when clicked, beyond showing its contents. Only the link
 * to the scoring screen (opened on that game) exists so far.
 */
export type GameTileAction = { kind: 'link'; to: '/team/score' };

interface GameResultsProps {
    /** Already-localized accessible name of the list of games. */
    caption: string;
    /** The table's teams in seat order; each keeps its seat colour. */
    teams: readonly ResultTeam[];
    /** Every game of the table: who plays it (at the trio table, two of the three teams). */
    schedule: readonly ScheduledGame[];
    /** The games entered so far. */
    games: readonly RecordedGame[];
    /** Makes every tile clickable; omitted, the tiles stay static. */
    gameAction?: GameTileAction;
}

/**
 * A table's games, read-only (admin rounds view, team overview): the teams'
 * Victory Points over the games entered so far on top, then one tile per game
 * with its two teams, their Tichu Points and the Victory Points the game earned
 * them. The winner's trophy takes the place of its colour dot; Beschiss and
 * double-win markers sit at the tile's foot, and a game not entered yet is a
 * dashed tile reading "missing". At the trio table each tile lists only the two
 * teams playing it. Tiles go two across, four (trio: three) once the container
 * is wide enough. With a [gameAction] (the team's panel while the table is
 * playing) the whole tile is clickable, its title being the link.
 */
export function GameResults({ caption, teams, schedule, games, gameAction }: GameResultsProps) {
    const byNumber = new Map(games.map((game) => [game.gameNumber, game]));

    return (
        <div className="@container flex flex-col gap-3">
            <VictoryPointStandings teams={teams} games={games} />
            <ol
                aria-label={caption}
                className={cn(
                    'grid grid-cols-2 gap-2',
                    teams.length > 2 ? '@md:grid-cols-3' : '@lg:grid-cols-4',
                )}
            >
                {schedule.map((scheduled) => (
                    <GameTile
                        key={scheduled.gameNumber}
                        teams={teams}
                        scheduled={scheduled}
                        game={byNumber.get(scheduled.gameNumber)}
                        action={gameAction}
                    />
                ))}
            </ol>
        </div>
    );
}

/**
 * Each team's Victory Points, the leader emphasised: "Alpha 5 : 2 Beta" for two
 * teams, one box per team in its seat colour at the trio table.
 */
function VictoryPointStandings({ teams, games }: Pick<GameResultsProps, 'teams' | 'games'>) {
    const { t } = useI18n();
    const points = teams.map((team) => totalVictoryPoints(games, team.id));
    const best = Math.max(...points);
    const labelId = useId();

    if (teams.length === 2) {
        return (
            <div className="flex flex-col items-center gap-0.5">
                <ul
                    aria-labelledby={labelId}
                    className="grid w-full grid-cols-2 items-center gap-2"
                >
                    {teams.map((team, seat) => (
                        <li
                            key={team.id}
                            // The second team mirrors the first, so the two scores meet in the middle.
                            className={cn(
                                'flex min-w-0 items-center justify-end gap-2',
                                seat === 1 && 'flex-row-reverse',
                            )}
                        >
                            <span className="min-w-0 truncate font-medium">
                                {seat === 0 ? <TeamDot tone={seatTone(seat).dot} /> : null}
                                {team.name}
                                {seat === 1 ? (
                                    <TeamDot tone={cn('mr-0 ml-1', seatTone(seat).dot)} />
                                ) : null}
                            </span>
                            <span
                                className={cn(
                                    'text-2xl font-semibold tabular-nums',
                                    points[seat] !== best && 'text-muted-foreground',
                                )}
                            >
                                {points[seat]}
                            </span>
                            {seat === 0 ? (
                                <span aria-hidden="true" className="text-2xl text-muted-foreground">
                                    :
                                </span>
                            ) : null}
                        </li>
                    ))}
                </ul>
                <span id={labelId} className="text-xs text-muted-foreground">
                    {t('rounds.victoryPoints')}
                </span>
            </div>
        );
    }

    return (
        <div className="flex flex-col gap-1">
            <span id={labelId} className="text-xs text-muted-foreground">
                {t('rounds.victoryPoints')}
            </span>
            <ul aria-labelledby={labelId} className="grid grid-cols-3 gap-2">
                {teams.map((team, seat) => (
                    <li
                        key={team.id}
                        className={cn(
                            'flex min-w-0 flex-col items-center rounded-md px-2 py-1',
                            seatTone(seat).chip,
                        )}
                    >
                        <span className="w-full truncate text-center text-xs font-medium">
                            {team.name}
                        </span>
                        <span
                            className={cn(
                                'text-xl tabular-nums',
                                points[seat] === best ? 'font-semibold' : 'opacity-70',
                            )}
                        >
                            {points[seat]}
                        </span>
                    </li>
                ))}
            </ul>
        </div>
    );
}

/** One game: its two teams with Tichu Points and Victory Points gained, winner marked. */
function GameTile({
    teams,
    scheduled,
    game,
    action,
}: {
    teams: readonly ResultTeam[];
    scheduled: ScheduledGame;
    game?: RecordedGame;
    action?: GameTileAction;
}) {
    const { t } = useI18n();
    const rows = scheduled.teamIds.map((id) => ({
        id,
        name: teams.find((team) => team.id === id)?.name ?? '',
        seat: teams.findIndex((team) => team.id === id),
        points: game && teamPoints(game, id),
        victoryPoints: game?.outcome.victoryPoints[id] ?? 0,
    }));
    const [first, second] = rows;
    // A double win decides the game outright, even when a failed call levels the
    // points; otherwise the team ahead wins. A tie, or a game not entered, has none.
    const winner =
        game?.outcome.doubleWinTeam ??
        (first?.points === undefined ||
        second?.points === undefined ||
        first.points === second.points
            ? undefined
            : first.points > second.points
              ? first.id
              : second.id);
    const title = `${t('rounds.game')} ${scheduled.gameNumber}`;

    return (
        <li
            className={cn(
                'relative flex flex-col gap-1 rounded-md border p-2',
                game ? 'border-border bg-card' : 'border-dashed border-input',
                // The stretched link covers the tile; the ring follows its keyboard focus.
                action &&
                    'hover:bg-accent has-[:focus-visible]:ring-2 has-[:focus-visible]:ring-ring',
            )}
        >
            <p className="flex items-center justify-between gap-1 text-xs text-muted-foreground">
                {action?.kind === 'link' ? (
                    // The link's box is stretched over the whole tile (the li is relative).
                    <Link
                        to={action.to}
                        search={{ game: scheduled.gameNumber }}
                        className="cursor-pointer font-medium outline-none after:absolute after:inset-0"
                    >
                        {title}
                    </Link>
                ) : (
                    <span className="font-medium">{title}</span>
                )}
                {game ? null : <span>{t('rounds.game.missing')}</span>}
            </p>
            <ul className="flex flex-col gap-1">
                {rows.map((row) => {
                    const won = row.id === winner;
                    return (
                        <li
                            key={row.id}
                            className={cn(
                                'flex items-center gap-1',
                                !game && 'text-muted-foreground',
                            )}
                        >
                            <span className={cn('min-w-0 flex-1 truncate', won && 'font-semibold')}>
                                {/* The winner's trophy takes the place of its colour dot. */}
                                {won ? (
                                    <span aria-hidden="true" className="mr-0.5 text-xs">
                                        🏆
                                    </span>
                                ) : (
                                    <TeamDot tone={seatTone(row.seat).dot} />
                                )}
                                {row.name}
                                {won ? (
                                    <span className="sr-only">{`, ${t('scoring.winner')}`}</span>
                                ) : null}
                            </span>
                            <span
                                className={cn(
                                    'tabular-nums',
                                    won ? 'font-semibold' : 'text-muted-foreground',
                                )}
                            >
                                {/* A team missing from the outcome shows a dash, never a misleading 0. */}
                                {row.points ?? <span aria-hidden="true">–</span>}
                            </span>
                            {/* The Victory Points this game earned the team; none reads as blank. */}
                            <span className="w-5 text-right text-xs text-muted-foreground tabular-nums">
                                {row.victoryPoints > 0 ? (
                                    <>
                                        {`+${row.victoryPoints}`}
                                        <span className="sr-only">{` ${t('rounds.victoryPoints')}`}</span>
                                    </>
                                ) : null}
                            </span>
                        </li>
                    );
                })}
            </ul>
            {game && (game.beschiss || game.outcome.doubleWinTeam !== null) ? (
                <p className="flex flex-wrap gap-1">
                    {game.beschiss ? (
                        <Badge variant="warning">{t('rounds.game.beschiss')}</Badge>
                    ) : null}
                    {game.outcome.doubleWinTeam !== null ? (
                        <Badge variant="secondary">{t('rounds.game.doubleWin')}</Badge>
                    ) : null}
                </p>
            ) : null}
        </li>
    );
}
