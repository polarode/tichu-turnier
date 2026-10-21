import type { TeamOverview } from '@/domain/team-overview.domain.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';
import { PageHeader } from '@/shared/components/page-header.component.tsx';

/**
 * The team's identity view (S2b): tournament name, team name and both players,
 * plus the neutral note that the match schedule appears once the tournament
 * starts. No confirm/retract controls — those arrive with the
 * confirmation-workflow slice.
 */
export function TeamOverview({ overview }: { overview: TeamOverview }) {
    const { t } = useI18n();
    const [playerOne, playerTwo] = overview.players;

    return (
        <section className="flex max-w-md flex-col gap-4">
            <PageHeader titleKey="team.title" descriptionKey="team.description" />
            <dl className="flex flex-col gap-2 text-sm">
                <div className="flex justify-between gap-4">
                    <dt className="text-muted-foreground">{t('teamOverview.tournament')}</dt>
                    <dd className="font-medium">{overview.tournamentName}</dd>
                </div>
                <div className="flex justify-between gap-4">
                    <dt className="text-muted-foreground">{t('teamOverview.teamName')}</dt>
                    <dd className="font-medium">{overview.teamName}</dd>
                </div>
                <div className="flex justify-between gap-4">
                    <dt className="text-muted-foreground">{t('teamOverview.playerOne')}</dt>
                    <dd className="font-medium">{playerOne?.name}</dd>
                </div>
                <div className="flex justify-between gap-4">
                    <dt className="text-muted-foreground">{t('teamOverview.playerTwo')}</dt>
                    <dd className="font-medium">{playerTwo?.name}</dd>
                </div>
            </dl>
            <p className="text-sm text-muted-foreground">{t('teamOverview.scheduleNote')}</p>
        </section>
    );
}
