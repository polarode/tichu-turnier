import { Pause } from 'lucide-react';
import { Badge } from '@tichu/ui/badge.tsx';
import { cn } from '@tichu/ui/lib/utils.ts';

import { type RecordedGame, teamPoints } from '@/domain/game.domain.ts';
import { seatTone } from '@/shared/constants/seat-tones.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';

/** Small colour marker tying a team to its seat colour. */
export function TeamDot({ tone }: { tone: string }) {
    return <span aria-hidden="true" className={cn('mr-1 inline-block size-2 rounded-full align-middle', tone)} />;
}

/**
 * One game of a table, read-only (admin rounds view, team overview): its matchup,
 * then either the total Tichu Points of the two playing teams in seat order (the
 * stored team order may differ) with the Beschiss and double-win markers, or a
 * muted "missing" when not yet entered. [sitsOut] marks a trio game the viewing
 * team does not play.
 */
export function GameSummaryRow({
    gameNumber,
    playing,
    game,
    teamName,
    seatOfTeam,
    sitsOut = false,
}: {
    gameNumber: number;
    playing: readonly [string, string];
    game: RecordedGame | undefined;
    teamName: (id: string) => string;
    seatOfTeam: (teamId: string) => number;
    sitsOut?: boolean;
}) {
    const { t } = useI18n();
    const label = `${t('rounds.game')} ${gameNumber}`;
    // A team missing from the outcome shows a dash, never a misleading 0.
    const points = (teamId: string) => (game && teamPoints(game, teamId)) ?? '–';

    return (
        <li aria-label={label} className="flex flex-wrap items-center gap-2">
            <span className="w-16 text-muted-foreground">{label}</span>
            <span>
                <TeamDot tone={seatTone(seatOfTeam(playing[0])).dot} />
                {`${teamName(playing[0])} ${t('rounds.versus')} `}
                <TeamDot tone={seatTone(seatOfTeam(playing[1])).dot} />
                {teamName(playing[1])}
            </span>
            {sitsOut ? (
                <>
                    <Pause aria-hidden="true" className="size-3 text-muted-foreground" />
                    <span className="sr-only">{t('scoring.game.sitsOut')}</span>
                </>
            ) : null}
            {game ? (
                <>
                    <span className="tabular-nums">{`${points(playing[0])} : ${points(playing[1])}`}</span>
                    {game.beschiss ? <Badge variant="warning">{t('rounds.game.beschiss')}</Badge> : null}
                    {game.outcome.doubleWinTeam !== null ? (
                        <Badge variant="secondary">{t('rounds.game.doubleWin')}</Badge>
                    ) : null}
                </>
            ) : (
                <span className="text-muted-foreground">{t('rounds.game.missing')}</span>
            )}
        </li>
    );
}
