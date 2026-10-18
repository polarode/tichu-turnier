import { useQueries } from '@tanstack/react-query';
import { Link } from '@tanstack/react-router';
import { Badge } from '@tichu/ui/badge.tsx';
import { buttonVariants } from '@tichu/ui/button.tsx';

import { teamsQueryOptions } from '@/features/teams/hooks/team-queries.hook.ts';
import { useTournaments } from '@/features/tournaments/hooks/tournament-queries.hook.ts';
import type { TranslationKey } from '@/shared/i18n/catalogs/en.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';
import { TournamentStatus } from '@/vocabulary/tournament-status.enum.ts';

const STATUS_LABEL_KEYS: Record<TournamentStatus, TranslationKey> = {
    [TournamentStatus.SETUP]: 'tournament.status.setup',
    [TournamentStatus.ACTIVE]: 'tournament.status.active',
    [TournamentStatus.COMPLETED]: 'tournament.status.completed',
};

const STATUS_BADGE_VARIANTS: Record<TournamentStatus, 'secondary' | 'success' | 'outline'> = {
    [TournamentStatus.SETUP]: 'secondary',
    [TournamentStatus.ACTIVE]: 'success',
    [TournamentStatus.COMPLETED]: 'outline',
};

/**
 * The organiser's landing screen: their tournaments with status and team count, an
 * empty state, and entry points to create or open one.
 *
 * Team counts come from the team-list endpoint, one query per tournament (cached under
 * the same key the setup screen uses) because the tournament response carries no count.
 */
export function Dashboard() {
    const { t } = useI18n();
    const tournamentsQuery = useTournaments();
    const tournaments = tournamentsQuery.data ?? [];
    const teamQueries = useQueries({
        queries: tournaments.map((tournament) => teamsQueryOptions(tournament.id)),
    });

    return (
        <section className="flex flex-col gap-6">
            <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="flex flex-col gap-1">
                    <h1 className="text-2xl font-semibold">{t('dashboard.title')}</h1>
                    <p className="text-muted-foreground">{t('dashboard.description')}</p>
                </div>
                <Link to="/admin/tournaments/new" className={buttonVariants({ size: 'sm' })}>
                    {t('dashboard.create')}
                </Link>
            </div>

            {tournamentsQuery.isPending ? (
                <p className="text-muted-foreground">{t('common.loading')}</p>
            ) : tournamentsQuery.isError ? (
                <p role="alert" className="text-sm text-destructive">
                    {t('dashboard.error')}
                </p>
            ) : tournaments.length === 0 ? (
                <div className="flex flex-col items-start gap-2 rounded-md border border-dashed border-border p-6">
                    <h2 className="text-lg font-medium">{t('dashboard.empty.title')}</h2>
                    <p className="text-muted-foreground">{t('dashboard.empty.description')}</p>
                </div>
            ) : (
                <ul className="flex flex-col gap-3">
                    {tournaments.map((tournament, index) => {
                        const teamCount = teamQueries[index]?.data?.length;
                        return (
                            <li
                                key={tournament.id}
                                className="flex flex-wrap items-center justify-between gap-4 rounded-md border border-border p-4"
                            >
                                <div className="flex flex-col gap-1">
                                    <span className="font-medium">{tournament.name}</span>
                                    <div className="flex items-center gap-3 text-sm text-muted-foreground">
                                        <Badge variant={STATUS_BADGE_VARIANTS[tournament.status]}>
                                            {t(STATUS_LABEL_KEYS[tournament.status])}
                                        </Badge>
                                        <span>{`${t('dashboard.teamCount')}: ${teamCount ?? '…'}`}</span>
                                    </div>
                                </div>
                                <Link
                                    to="/admin/tournaments/$tournamentId"
                                    params={{ tournamentId: tournament.id }}
                                    className={buttonVariants({ variant: 'outline', size: 'sm' })}
                                >
                                    {t('dashboard.open')}
                                </Link>
                            </li>
                        );
                    })}
                </ul>
            )}
        </section>
    );
}
