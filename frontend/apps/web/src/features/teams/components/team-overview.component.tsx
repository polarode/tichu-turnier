import { Link } from '@tanstack/react-router';
import { buttonVariants } from '@tichu/ui/button.tsx';

import type { TeamOverview } from '@/domain/team-overview.domain.ts';
import { TournamentStatus } from '@/vocabulary/tournament-status.enum.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';
import { PageHeader } from '@/shared/components/page-header.component.tsx';

/**
 * The team's identity view (S2b): tournament name, team name and both players,
 * plus one state-dependent slot below them: while the tournament is still in
 * setup it shows the neutral note that the match schedule appears once the
 * tournament starts; once it has started the slot becomes the entry point to
 * the scoring screen (S5c, #101) and the note goes. No confirm/retract
 * controls — those arrive with the confirmation-workflow slice.
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
            {/* Either the schedule note (tournament still in setup) or the
                scoring link (tournament started) — never both. The scoring
                screen still guards itself (401 → login, not seated →
                placeholder). */}
            {overview.tournamentStatus === TournamentStatus.SETUP ? (
                <p className="text-sm text-muted-foreground">{t('teamOverview.scheduleNote')}</p>
            ) : (
                <Link to="/team/score" className={buttonVariants()}>
                    {t('teamOverview.scoringLink')}
                </Link>
            )}
        </section>
    );
}
