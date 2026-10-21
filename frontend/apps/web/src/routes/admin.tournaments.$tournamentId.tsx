import { Link, createFileRoute } from '@tanstack/react-router';
import { Badge } from '@tichu/ui/badge.tsx';
import { buttonVariants } from '@tichu/ui/button.tsx';

import { useTournament } from '@/features/tournaments/hooks/tournament-queries.hook.ts';
import { PageHeader } from '@/shared/components/page-header.component.tsx';
import { localizedErrorMessage } from '@/shared/forms/server-error.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';
import type { TranslationKey } from '@/shared/i18n/catalogs/en.ts';
import { TournamentStatus } from '@/vocabulary/tournament-status.enum.ts';

const STATUS_LABEL_KEYS: Record<TournamentStatus, TranslationKey> = {
    [TournamentStatus.SETUP]: 'tournament.status.setup',
    [TournamentStatus.ACTIVE]: 'tournament.status.active',
    [TournamentStatus.COMPLETED]: 'tournament.status.completed',
};

/**
 * The tournament detail screen. Team management arrives with #18; for now it shows the
 * tournament's identity and status, plus the edit entry — which only exists while the
 * tournament is still in setup, since editing is locked afterwards.
 */
function TournamentDetailComponent() {
        const { t } = useI18n();
        const { tournamentId } = Route.useParams();
        const tournamentQuery = useTournament(tournamentId);

        if (tournamentQuery.isPending) {
            return <p className="text-muted-foreground">{t('common.loading')}</p>;
        }
        if (tournamentQuery.isError) {
            return (
                <p role="alert" className="text-sm text-destructive">
                    {localizedErrorMessage(tournamentQuery.error, t)}
                </p>
            );
        }

        const tournament = tournamentQuery.data;
        const editable = tournament.status === TournamentStatus.SETUP;

        return (
            <section className="flex flex-col gap-6">
                <div className="flex flex-wrap items-start justify-between gap-3">
                    <PageHeader
                        titleKey="tournament.detail.title"
                        descriptionKey="tournament.detail.description"
                    />
                    {editable ? (
                        <Link
                            to="/admin/tournaments/$tournamentId/edit"
                            params={{ tournamentId: tournament.id }}
                            className={buttonVariants({ variant: 'outline', size: 'sm' })}
                        >
                            {t('tournament.detail.edit')}
                        </Link>
                    ) : null}
                </div>

                <div className="flex flex-col gap-2 rounded-md border border-border p-4">
                    <div className="flex items-center gap-3">
                        <span className="text-lg font-medium">{tournament.name}</span>
                        <Badge variant="secondary">
                            {t(STATUS_LABEL_KEYS[tournament.status])}
                        </Badge>
                    </div>
                    {tournament.description ? (
                        <p className="text-muted-foreground">{tournament.description}</p>
                    ) : null}
                    <p className="text-sm text-muted-foreground">
                        {`${t('tournament.form.maxTeams')}: ${tournament.maxTeams}`}
                    </p>
                    <p className="text-sm text-muted-foreground">
                        {t('tournament.form.allowGrandTichu')}
                        {': '}
                        {tournament.settings.allowGrandTichu ? t('common.yes') : t('common.no')}
                    </p>
                </div>
        </section>
    );
}

export const Route = createFileRoute('/admin/tournaments/$tournamentId')({
    component: TournamentDetailComponent,
});
