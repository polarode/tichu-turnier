import type { TeamOverview } from '@/domain/team-overview.domain.ts';
import { TableConfirmationPanel } from '@/features/scoring/components/table-confirmation-panel.component.tsx';
import { TournamentStatus } from '@/vocabulary/tournament-status.enum.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';
import { PageHeader } from '@/shared/components/page-header.component.tsx';

/**
 * The team's identity view (S2b): a compact card with tournament name, team
 * name and both players, plus one state-dependent slot below it: while the
 * tournament is still in setup it shows the neutral note that the match
 * schedule appears once the tournament starts; once it has started the slot
 * becomes the current table's confirmation panel (S6b, #106), which also holds
 * the entry point to the scoring screen (S5c, #101), and the note goes.
 */
export function TeamOverview({ overview }: { overview: TeamOverview }) {
    const { t } = useI18n();
    const [playerOne, playerTwo] = overview.players;

    return (
        <section className="flex w-full max-w-md flex-col gap-4">
            <PageHeader titleKey="team.title" />
            {/* A compact identity card: the hierarchy (tournament, team, players)
                carries the meaning visually; the labels stay for screen readers. */}
            <dl className="flex flex-col gap-0.5 rounded-md border border-border bg-card px-4 py-3 break-words">
                <dt className="sr-only">{t('teamOverview.tournament')}</dt>
                <dd className="text-xs text-muted-foreground">{overview.tournamentName}</dd>
                <dt className="sr-only">{t('teamOverview.teamName')}</dt>
                <dd className="text-lg font-semibold">{overview.teamName}</dd>
                <dt className="sr-only">{t('teamOverview.players')}</dt>
                <dd className="text-sm text-muted-foreground">
                    <span>{playerOne?.name}</span>
                    <span aria-hidden="true"> · </span>
                    <span>{playerTwo?.name}</span>
                </dd>
            </dl>
            {/* Either the schedule note (tournament still in setup) or the
                confirmation panel with its scoring link (tournament started) —
                never both. While the team is not seated the panel shrinks to
                the scoring link; the scoring screen still guards itself
                (401 → login, not seated → placeholder). */}
            {overview.tournamentStatus === TournamentStatus.SETUP ? (
                <p className="text-sm text-muted-foreground">{t('teamOverview.scheduleNote')}</p>
            ) : (
                <TableConfirmationPanel viewerTeamId={overview.teamId} />
            )}
        </section>
    );
}
