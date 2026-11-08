import { useEffect, useState } from 'react';
import { useQueries } from '@tanstack/react-query';
import { Link } from '@tanstack/react-router';
import { X } from 'lucide-react';
import { Badge } from '@tichu/ui/badge.tsx';
import { Button, buttonVariants } from '@tichu/ui/button.tsx';

import { teamsQueryOptions } from '@/features/teams/hooks/team-queries.hook.ts';
import { TournamentStatusBadge } from '@/features/tournaments/components/tournament-status-badge.component.tsx';
import { useTournaments } from '@/features/tournaments/hooks/tournament-queries.hook.ts';
import { fillPlaceholders } from '@/shared/i18n/fill-placeholders.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';
import { useNoticeStore, type Notice } from '@/shared/stores/notice.store.ts';
import { OrganizerRole } from '@/vocabulary/organizer-role.enum.ts';

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

            <DashboardNotice />

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
                        const coOrganized = tournament.organizerRole === OrganizerRole.CO;
                        return (
                            <li
                                key={tournament.id}
                                className="flex flex-wrap items-center justify-between gap-4 rounded-md border border-border bg-card p-4"
                            >
                                <div className="flex flex-col gap-1">
                                    <span className="font-medium">{tournament.name}</span>
                                    <div className="flex flex-wrap items-center gap-3 text-sm text-muted-foreground">
                                        <TournamentStatusBadge status={tournament.status} />
                                        {coOrganized ? (
                                            <Badge variant="outline">
                                                {t('organizers.coOrganizer')}
                                            </Badge>
                                        ) : null}
                                        <span>{`${t('dashboard.teamCount')}: ${teamCount ?? '…'}`}</span>
                                    </div>
                                    {coOrganized ? (
                                        <span className="text-xs text-muted-foreground">
                                            {`${t('dashboard.ledBy')} ${tournament.leadOrganizerEmail}`}
                                        </span>
                                    ) : null}
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

/**
 * The notice a previous screen handed over. It is read once, on mount: a notice that arrives
 * while the dashboard is already open is not shown. The store is cleared when the dashboard
 * unmounts; the banner keeps its own copy, because StrictMode runs that cleanup right after
 * mount in development and must not hide the banner.
 */
function DashboardNotice() {
    const { t } = useI18n();
    const [notice, setNotice] = useState<Notice | null>(() => useNoticeStore.getState().notice);
    const dismissStored = useNoticeStore((state) => state.dismiss);

    useEffect(() => () => dismissStored(), [dismissStored]);

    if (!notice) return null;

    const dismiss = () => {
        setNotice(null);
        dismissStored();
    };

    return (
        <div
            role="status"
            className="flex items-center justify-between gap-3 rounded-md bg-muted px-3 py-2 text-sm"
        >
            <span>{fillPlaceholders(t(notice.key), notice.values)}</span>
            <Button
                type="button"
                variant="ghost"
                size="sm"
                aria-label={t('organizers.notice.dismiss')}
                onClick={dismiss}
            >
                <X className="size-4" aria-hidden="true" />
            </Button>
        </div>
    );
}
