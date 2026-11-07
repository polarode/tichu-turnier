import { Badge } from '@tichu/ui/badge.tsx';

import type { PublicRound, PublicTable } from '@/domain/tournament.domain.ts';
import { usePublicTournament } from '@/features/public-tournaments/hooks/public-tournaments.hook.ts';
import { TeamDot } from '@/features/scoring/components/game-results-table.component.tsx';
import { tableHeading } from '@/features/scoring/labels.ts';
import { TournamentStatusBadge } from '@/features/tournaments/components/tournament-status-badge.component.tsx';
import { ApiError } from '@/shared/api/api-error.ts';
import { BackLink } from '@/shared/components/back-link.component.tsx';
import { seatTone } from '@/shared/constants/seat-tones.ts';
import { localizedErrorMessage } from '@/shared/forms/server-error.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';

interface PublicTournamentPageProps {
    tournamentId: string;
}

/**
 * The public tournament page: the pairings of every drawn round, newest on top, and
 * nothing else (no results, standings or table status, in any status). Anonymous, so
 * no session is needed; a tournament that is not public reads as not found.
 */
export function PublicTournamentPage({ tournamentId }: PublicTournamentPageProps) {
    // Outside the body, so loading, not-found and error states keep a way back too.
    return (
        <div className="flex flex-col gap-4">
            <BackLink to="/public" labelKey="publicTournament.backLink" />
            <PublicTournamentBody tournamentId={tournamentId} />
        </div>
    );
}

function PublicTournamentBody({ tournamentId }: { tournamentId: string }) {
    const { t } = useI18n();
    const tournamentQuery = usePublicTournament(tournamentId);

    if (tournamentQuery.isPending) {
        return <p className="text-muted-foreground">{t('common.loading')}</p>;
    }
    if (tournamentQuery.isError) {
        if (isNotFound(tournamentQuery.error)) {
            return (
                <p role="alert" className="text-destructive">
                    {t('publicTournament.notFound')}
                </p>
            );
        }
        return (
            <p role="alert" className="text-sm text-destructive">
                {localizedErrorMessage(tournamentQuery.error, t)}
            </p>
        );
    }

    const tournament = tournamentQuery.data;
    const roundsNewestFirst = [...tournament.rounds].sort((a, b) => b.number - a.number);

    return (
        <section className="flex flex-col gap-6">
            <div className="flex flex-wrap items-center gap-3">
                <h1 className="text-xl font-semibold sm:text-2xl">{tournament.name}</h1>
                <TournamentStatusBadge status={tournament.status} />
            </div>

            {roundsNewestFirst.length === 0 ? (
                <p className="text-muted-foreground">{t('publicTournament.noRounds')}</p>
            ) : (
                roundsNewestFirst.map((round) => (
                    <PublicRoundSection key={round.number} round={round} />
                ))
            )}
        </section>
    );
}

/** The backend answers NOT_FOUND for setup, non-public and unknown tournaments alike. */
function isNotFound(error: unknown): boolean {
    return error instanceof ApiError && error.code === 'NOT_FOUND';
}

function PublicRoundSection({ round }: { round: PublicRound }) {
    const { t } = useI18n();
    const label = `${t('rounds.round')} ${round.number}`;

    return (
        <section aria-label={label} className="flex flex-col gap-3">
            <h2 className="text-lg font-medium">{label}</h2>
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
                {round.tables.map((table) => (
                    <PublicTableCard key={table.tableNumber} table={table} />
                ))}
            </div>
        </section>
    );
}

function PublicTableCard({ table }: { table: PublicTable }) {
    const { t } = useI18n();

    return (
        <article className="flex flex-col gap-3 rounded-md border border-border bg-card p-4">
            <div className="flex items-center justify-between gap-2">
                <h3 className="font-medium">{tableHeading(table.tableNumber, t)}</h3>
                {table.trio ? <Badge variant="secondary">{t('rounds.trio')}</Badge> : null}
            </div>
            <ul className="flex flex-col gap-2">
                {table.teams.map((team, seat) => (
                    <li key={team.id} className="flex flex-col gap-0.5">
                        <span className="font-medium">
                            <TeamDot tone={seatTone(seat).dot} />
                            {team.name}
                        </span>
                        <span className="text-sm text-muted-foreground">
                            {team.players.map((player) => player.name).join(' · ')}
                        </span>
                    </li>
                ))}
            </ul>
        </article>
    );
}
