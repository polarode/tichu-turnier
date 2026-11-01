import { Badge } from '@tichu/ui/badge.tsx';

import { type RecordedGame, teamPoints } from '@/domain/game.domain.ts';
import type { TableOverview } from '@/domain/round.domain.ts';
import type { Team } from '@/domain/team.domain.ts';
import { useRounds } from '@/features/tournaments/hooks/start-rounds-queries.hook.ts';
import { localizedErrorMessage } from '@/shared/forms/server-error.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';

interface RoundsSectionProps {
    tournamentId: string;
    /** Team names for the id → name resolution of the table assignments. */
    teams: readonly Team[];
    /** The tournament is ACTIVE: results are coming in, so the rounds poll. */
    live?: boolean;
}

/**
 * The rounds view: one block per round with the table assignments rendered as
 * "Table N: Team A vs Team B". Team names resolve from the teams query; a team that
 * has since disappeared falls back to its raw id so the pairing stays legible.
 * Each table shows its game status ("x/n entered") and the entered games
 * read-only (S5d, #102) — scoring happens in the team area.
 */
export function RoundsSection({ tournamentId, teams, live = false }: RoundsSectionProps) {
    const { t } = useI18n();
    const roundsQuery = useRounds(tournamentId, { live });

    if (roundsQuery.isPending) {
        return null;
    }
    if (roundsQuery.isError) {
        return (
            <section aria-label={t('rounds.section.title')} className="flex flex-col gap-3">
                <h2 className="text-lg font-semibold">{t('rounds.section.title')}</h2>
                <p role="alert" className="text-sm text-destructive">
                    {localizedErrorMessage(roundsQuery.error, t)}
                </p>
            </section>
        );
    }

    const rounds = roundsQuery.data;
    if (rounds.length === 0) {
        return null;
    }

    const teamName = (id: string) => teams.find((team) => team.id === id)?.name ?? id;

    return (
        <section aria-label={t('rounds.section.title')} className="flex flex-col gap-3">
            <h2 className="text-lg font-semibold">{t('rounds.section.title')}</h2>
            {rounds.map((round) => (
                <div
                    key={round.number}
                    className="flex flex-col gap-2 rounded-md border border-border p-4"
                >
                    <h3 className="font-medium">{`${t('rounds.round')} ${round.number}`}</h3>
                    <ul className="flex flex-col gap-3">
                        {round.tables.map((table) => (
                            <li key={table.tableNumber} className="flex flex-col gap-1 text-sm">
                                <div className="flex flex-wrap items-baseline justify-between gap-2">
                                    <span>
                                        <span className="font-medium">
                                            {`${t('rounds.table')} ${table.tableNumber}`}
                                        </span>
                                        {': '}
                                        {teamName(table.homeTeamId)} {t('rounds.versus')}{' '}
                                        {teamName(table.awayTeamId)}
                                    </span>
                                    <span className="text-muted-foreground">
                                        {`${table.games.length}/${table.gameCount} ${t('rounds.games.entered')}`}
                                    </span>
                                </div>
                                {table.games.length > 0 ? (
                                    <ul className="flex flex-col gap-1 pl-4">
                                        {table.games.map((game) => (
                                            <GameRow key={game.gameNumber} table={table} game={game} />
                                        ))}
                                    </ul>
                                ) : null}
                            </li>
                        ))}
                    </ul>
                </div>
            ))}
        </section>
    );
}

/**
 * One entered game: total Tichu Points ordered home : away by team id (the stored
 * team order may differ from the table's seating), plus the Beschiss and
 * double-win markers.
 */
function GameRow({ table, game }: { table: TableOverview; game: RecordedGame }) {
    const { t } = useI18n();
    const label = `${t('rounds.game')} ${game.gameNumber}`;
    // A team missing from the outcome shows a dash, never a misleading 0.
    const points = (teamId: string) => teamPoints(game, teamId) ?? '–';

    return (
        <li aria-label={label} className="flex flex-wrap items-center gap-2">
            <span className="w-16 text-muted-foreground">{label}</span>
            <span className="tabular-nums">{`${points(table.homeTeamId)} : ${points(table.awayTeamId)}`}</span>
            {game.beschiss ? <Badge variant="warning">{t('rounds.game.beschiss')}</Badge> : null}
            {game.outcome.doubleWinTeam !== null ? (
                <Badge variant="secondary">{t('rounds.game.doubleWin')}</Badge>
            ) : null}
        </li>
    );
}
