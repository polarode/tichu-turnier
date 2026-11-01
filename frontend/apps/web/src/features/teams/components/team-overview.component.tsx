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
        <section className="flex w-full max-w-md flex-col gap-4">
            <PageHeader titleKey="team.title" descriptionKey="team.description" />
            <dl className="flex flex-col divide-y divide-border rounded-md border border-border text-sm">
                <div className="flex items-baseline justify-between gap-4 px-4 py-3">
                    <dt className="shrink-0 text-muted-foreground">{t('teamOverview.tournament')}</dt>
                    <dd className="min-w-0 text-right font-medium break-words">{overview.tournamentName}</dd>
                </div>
                <div className="flex items-baseline justify-between gap-4 px-4 py-3">
                    <dt className="shrink-0 text-muted-foreground">{t('teamOverview.teamName')}</dt>
                    <dd className="min-w-0 text-right font-medium break-words">{overview.teamName}</dd>
                </div>
                <div className="flex items-baseline justify-between gap-4 px-4 py-3">
                    <dt className="shrink-0 text-muted-foreground">{t('teamOverview.playerOne')}</dt>
                    <dd className="min-w-0 text-right font-medium break-words">{playerOne?.name}</dd>
                </div>
                <div className="flex items-baseline justify-between gap-4 px-4 py-3">
                    <dt className="shrink-0 text-muted-foreground">{t('teamOverview.playerTwo')}</dt>
                    <dd className="min-w-0 text-right font-medium break-words">{playerTwo?.name}</dd>
                </div>
            </dl>
            {/* Either the schedule note (tournament still in setup) or the
                scoring link (tournament started) — never both. The scoring
                screen still guards itself (401 → login, not seated →
                placeholder). */}
            {overview.tournamentStatus === TournamentStatus.SETUP ? (
                <p className="text-sm text-muted-foreground">{t('teamOverview.scheduleNote')}</p>
            ) : (
                <Link
                    to="/team/score"
                    className={buttonVariants({ size: 'touch', className: 'w-full sm:w-auto sm:self-start' })}
                >
                    {t('teamOverview.scoringLink')}
                </Link>
            )}
        </section>
    );
}
