import { useId } from 'react';
import { Link } from '@tanstack/react-router';
import { ChevronRight } from 'lucide-react';
import { Badge } from '@tichu/ui/badge.tsx';
import { cn } from '@tichu/ui/lib/utils.ts';

import {
    type ForfeitGame,
    isLockedByWithdrawal,
    isSettled,
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
 * What a game tile does when clicked, beyond showing its contents: a link to the
 * scoring screen (the team panel, for entry or to view the game read-only), or a button that hands
 * the game number to its caller (the organizer's correction on a completed table,
 * ADR 0010). The button's [label] is its accessible name, already localized.
 * A [readOnly] link only views its game (the table froze or the tournament ended).
 */
export type GameTileAction =
    | { kind: 'link'; to: '/team/score'; readOnly: boolean }
    | {
          kind: 'button';
          onSelect: (gameNumber: number) => void;
          label: (gameNumber: number) => string;
      };

interface GameResultsProps {
    /** Already-localized accessible name of the list of games. */
    caption: string;
    /** The table's teams in seat order; each keeps its seat colour. */
    teams: readonly ResultTeam[];
    /** Every game of the table: who plays it (at the trio table, two of the three teams). */
    schedule: readonly ScheduledGame[];
    /** The games entered so far. */
    games: readonly RecordedGame[];
    /** The table's Forfeit Games (ADR 0011): shown as forfeited tiles among the games, never as played. */
    forfeits: readonly ForfeitGame[];
    /** Makes every tile clickable; omitted, the tiles stay static. */
    gameAction?: GameTileAction;
    /**
     * The seated teams that withdrew (ADR 0011). A game against one of them is read-only
     * (a link only views it), never the next game to enter.
     */
    withdrawnTeamIds?: readonly string[];
}

/**
 * A table's games, read-only (admin rounds view, team overview): the teams'
 * Victory Points over the games entered so far on top, then one tile per game
 * with its two teams, their Tichu Points and the Victory Points the game earned
 * them.
 *
 * A Forfeit Game (ADR 0011) is a tile of its own: the awarded team wins it with
 * the forfeit's points, the withdrawn team scores 0, and it is badged forfeited.
 * A forfeit that awards no Victory Points (no compensation) has no winner. The
 * winner's trophy takes the place of its colour dot; Beschiss and double-win
 * markers sit at the tile's foot, as does a neutral "corrected by the organizer"
 * marker on a game an organizer corrected (ADR 0010). A game not entered yet is a
 * dashed tile reading "missing".
 *
 * At the trio table each tile lists only the two teams playing it. Tiles go two
 * across, four (trio: three) once the container is wide enough. With a
 * [gameAction] the whole tile is clickable, its title being the control: the team
 * panel's link to the scoring screen, or the organizer's correction button on a
 * completed table. A clickable tile ends in a footer naming what a tap does
 * (enter, edit, view, correct), and while games can still be entered the first
 * missing one is ringed as next up.
 */
export function GameResults({
    caption,
    teams,
    schedule,
    games,
    forfeits,
    gameAction,
    withdrawnTeamIds = [],
}: GameResultsProps) {
    const byNumber = new Map(games.map((game) => [game.gameNumber, game]));
    const forfeitByNumber = new Map(forfeits.map((forfeit) => [forfeit.gameNumber, forfeit]));
    // The next game to enter: the first missing one, at a trio table too (even one
    // the viewing team sits out). A forfeited game is not missing, and neither is a
    // game against a withdrawn team, which is never entered. Nothing is next once the
    // links only view.
    const nextGame =
        gameAction?.kind === 'link' && !gameAction.readOnly
            ? schedule.find(
                  (scheduled) =>
                      !isSettled(scheduled.gameNumber, games, forfeits) &&
                      !isLockedByWithdrawal(scheduled, withdrawnTeamIds),
              )?.gameNumber
            : undefined;

    return (
        <div className="@container flex flex-col gap-3">
            <VictoryPointStandings teams={teams} games={games} forfeits={forfeits} />
            <ol
                aria-label={caption}
                // Phones get a little more room around and inside the tiles; from @lg up
                // (four tiles a row) the denser spacing returns.
                className={cn(
                    'grid grid-cols-2 gap-3 @lg:gap-2',
                    teams.length > 2 ? '@md:grid-cols-3' : '@lg:grid-cols-4',
                )}
            >
                {schedule.map((scheduled) => (
                    <GameTile
                        key={scheduled.gameNumber}
                        teams={teams}
                        scheduled={scheduled}
                        game={byNumber.get(scheduled.gameNumber)}
                        forfeit={forfeitByNumber.get(scheduled.gameNumber)}
                        settled={isSettled(scheduled.gameNumber, games, forfeits)}
                        action={gameAction}
                        next={scheduled.gameNumber === nextGame}
                        locked={isLockedByWithdrawal(scheduled, withdrawnTeamIds)}
                    />
                ))}
            </ol>
        </div>
    );
}

/**
 * Each team's Victory Points, one tile per team in its seat colour. A sole leader's
 * tile is ringed in its colour and announced as leading; a tie at the top, or a
 * table with no points yet, has no leader.
 */
function VictoryPointStandings({
    teams,
    games,
    forfeits,
}: Pick<GameResultsProps, 'teams' | 'games' | 'forfeits'>) {
    const { t } = useI18n();
    const points = teams.map(
        (team) => totalVictoryPoints(games, team.id) + forfeitVictoryPoints(forfeits, team.id),
    );
    const best = Math.max(...points);
    const leader =
        best > 0 && points.filter((p) => p === best).length === 1 ? points.indexOf(best) : -1;
    const labelId = useId();

    return (
        <div className="flex flex-col gap-1">
            <span id={labelId} className="text-xs text-muted-foreground">
                {t('rounds.victoryPoints')}
            </span>
            <ul
                aria-labelledby={labelId}
                className={cn('grid gap-2', teams.length > 2 ? 'grid-cols-3' : 'grid-cols-2')}
            >
                {teams.map((team, seat) => (
                    <li
                        key={team.id}
                        className={cn(
                            'flex min-w-0 flex-col items-center rounded-md px-2 py-1',
                            seatTone(seat).chip,
                            // The chip's text is the team colour, so the ring takes it too.
                            seat === leader && 'ring-2 ring-current',
                        )}
                    >
                        <span className="w-full truncate text-center text-xs font-medium">
                            {team.name}
                            {seat === leader ? (
                                <span className="sr-only">{`, ${t('rounds.victoryPoints.leader')}`}</span>
                            ) : null}
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

/** The Victory Points a team earned from Forfeit Games (ADR 0011): only the awarded side scores. */
function forfeitVictoryPoints(forfeits: readonly ForfeitGame[], teamId: string): number {
    return forfeits
        .filter((forfeit) => forfeit.awardedTeamId === teamId)
        .reduce((sum, forfeit) => sum + forfeit.victoryPoints, 0);
}

/**
 * The team a forfeit wins (ADR 0011): the awarded team, but only when the forfeit gains
 * Victory Points. A forfeit that compensates nothing (no compensation) has no winner.
 */
function forfeitWinner(forfeit: ForfeitGame): string | undefined {
    return forfeit.victoryPoints > 0 ? (forfeit.awardedTeamId ?? undefined) : undefined;
}

/** One game: its two teams with Tichu Points and Victory Points gained, winner marked. */
function GameTile({
    teams,
    scheduled,
    game,
    forfeit,
    settled,
    action: gameAction,
    next,
    locked,
}: {
    teams: readonly ResultTeam[];
    scheduled: ScheduledGame;
    game?: RecordedGame;
    /** The game was forfeited instead of played (ADR 0011); the awarded team wins it. */
    forfeit?: ForfeitGame;
    /** Recorded or forfeited (see `isSettled`): the tile reads as done rather than missing. */
    settled: boolean;
    action?: GameTileAction;
    /** The next game to enter: ringed, badged, and its footer filled. */
    next: boolean;
    /** A team of the game withdrew (ADR 0011): the game is only viewed, never entered. */
    locked: boolean;
}) {
    const { t } = useI18n();
    // A forfeited game has nothing to enter, edit or correct, so its tile is not a control.
    // A game against a withdrawn team can only be viewed.
    const action: GameTileAction | undefined = forfeit
        ? undefined
        : locked && gameAction?.kind === 'link'
          ? { ...gameAction, readOnly: true }
          : gameAction;
    const rows = scheduled.teamIds.map((id) => ({
        id,
        name: teams.find((team) => team.id === id)?.name ?? '',
        seat: teams.findIndex((team) => team.id === id),
        points: forfeit
            ? forfeit.awardedTeamId === id
                ? forfeit.tichuPoints
                : 0
            : game && teamPoints(game, id),
        victoryPoints: forfeit
            ? forfeit.awardedTeamId === id
                ? forfeit.victoryPoints
                : 0
            : (game?.outcome.victoryPoints[id] ?? 0),
    }));
    const [first, second] = rows;
    // A double win decides the game outright, even when a failed call levels the
    // points; otherwise the team ahead wins. A tie, or a game not entered, has none.
    const winner = forfeit
        ? forfeitWinner(forfeit)
        : (game?.outcome.doubleWinTeam ??
          (first?.points === undefined ||
          second?.points === undefined ||
          first.points === second.points
              ? undefined
              : first.points > second.points
                ? first.id
                : second.id));
    const title = `${t('rounds.game')} ${scheduled.gameNumber}`;
    const footerLabel = !action
        ? undefined
        : action.kind === 'button'
          ? t('rounds.game.action.correct')
          : action.readOnly
            ? t('rounds.game.action.view')
            : game
              ? t('rounds.game.action.edit')
              : t('rounds.game.action.enter');

    return (
        <li
            className={cn(
                'relative flex flex-col gap-1.5 rounded-md border p-3 @lg:gap-1 @lg:p-2',
                settled ? 'border-border bg-card' : 'border-dashed border-input',
                // The stretched link covers the tile; the ring follows its keyboard focus.
                action &&
                    'hover:bg-accent has-[:focus-visible]:ring-2 has-[:focus-visible]:ring-ring',
                // The footer runs flush to the tile's edges and bottom.
                footerLabel && 'overflow-hidden pb-0 @lg:pb-0',
                next && 'border-solid border-primary ring-2 ring-primary/30',
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
                ) : action?.kind === 'button' ? (
                    // Like the link, the button's box is stretched over the whole tile.
                    <button
                        type="button"
                        aria-label={action.label(scheduled.gameNumber)}
                        onClick={() => action.onSelect(scheduled.gameNumber)}
                        className="cursor-pointer font-medium outline-none after:absolute after:inset-0"
                    >
                        {title}
                    </button>
                ) : (
                    <span className="font-medium">{title}</span>
                )}
                {next ? (
                    <Badge>{t('rounds.game.next')}</Badge>
                ) : settled ? null : (
                    // A footer already says what to do and is hidden from screen readers,
                    // so "missing" is announced only.
                    <span className={cn(footerLabel && 'sr-only')}>{t('rounds.game.missing')}</span>
                )}
            </p>
            <ul className="flex flex-col gap-1.5 @lg:gap-1">
                {rows.map((row) => {
                    const won = row.id === winner;
                    return (
                        <li
                            key={row.id}
                            className={cn(
                                'flex items-center gap-1',
                                !settled && 'text-muted-foreground',
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
            {forfeit ? (
                <p className="flex flex-wrap gap-1">
                    <Badge variant="outline">{t('rounds.game.forfeited')}</Badge>
                </p>
            ) : null}
            {game && (game.beschiss || game.outcome.doubleWinTeam !== null || game.correction) ? (
                <p className="flex flex-wrap gap-1">
                    {game.beschiss ? (
                        <Badge variant="warning">{t('rounds.game.beschiss')}</Badge>
                    ) : null}
                    {game.outcome.doubleWinTeam !== null ? (
                        <Badge variant="secondary">{t('rounds.game.doubleWin')}</Badge>
                    ) : null}
                    {game.correction ? (
                        <Badge variant="outline">{t('rounds.game.corrected')}</Badge>
                    ) : null}
                </p>
            ) : null}
            {/* What a tap does, for sighted users; the tile's link already says it opens. */}
            {footerLabel ? (
                <span
                    aria-hidden="true"
                    className={cn(
                        '-mx-3 mt-auto flex items-center justify-between border-t px-3 py-2 text-xs font-medium @lg:-mx-2 @lg:px-2 @lg:py-1.5',
                        next
                            ? 'border-primary bg-primary text-primary-foreground'
                            : game
                              ? 'border-border bg-muted/60'
                              : 'border-dashed border-input',
                    )}
                >
                    {footerLabel}
                    <ChevronRight className="size-4" />
                </span>
            ) : null}
        </li>
    );
}
