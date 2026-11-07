import type { TeamOverview } from '@/domain/team-overview.domain.ts';
import { StandingsTable } from '@/shared/components/standings-table.component.tsx';
import { useFinalRanking } from '@/features/teams/hooks/team-auth-queries.hook.ts';
import {
    ScoringLink,
    TableConfirmationPanel,
} from '@/features/scoring/components/table-confirmation-panel.component.tsx';
import { LiveBadge } from '@/shared/components/live-badge.component.tsx';
import { LoadingIndicator } from '@/shared/components/loading-indicator.component.tsx';
import { localizedErrorMessage } from '@/shared/forms/server-error.ts';
import { TournamentStatus } from '@/vocabulary/tournament-status.enum.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';

/**
 * The team's identity view (S2b): a compact card with tournament name, team
 * name and both players, plus one state-dependent slot below it:
 * - SETUP: the neutral note that the match schedule appears once the tournament
 *   starts.
 * - ACTIVE: the current table's confirmation panel (S6b, #106), which also holds
 *   the entry point to the scoring screen (S5c, #101), with its write controls.
 * - COMPLETED (S7c, #116): a neutral note that the results are announced at the
 *   ceremony, above the same panel, now read-only (no Confirm, no Retract).
 * - PUBLISHED (S7c, #116): the Final Ranking table, with the own team's row
 *   marked, and below it the read-only scoring link, so the last table stays
 *   reachable. The ranking is only fetched once published.
 */
export function TeamOverview({
    overview,
    live,
    connected,
}: {
    overview: TeamOverview;
    /** Whether the live stream applies (the tournament is not yet published). */
    live: boolean;
    /** Whether the live stream is connected; only read while `live`. */
    connected: boolean;
}) {
    const { t } = useI18n();
    const [playerOne, playerTwo] = overview.players;
    const published = overview.tournamentStatus === TournamentStatus.PUBLISHED;
    const finalRanking = useFinalRanking(published);

    function renderRanking() {
        if (finalRanking.isPending) return <LoadingIndicator />;
        if (finalRanking.isError) {
            return (
                <p role="alert" className="text-sm text-destructive">
                    {localizedErrorMessage(finalRanking.error, t)}
                </p>
            );
        }
        return (
            <StandingsTable
                entries={finalRanking.data}
                caption={t('standings.finalTitle')}
                highlightTeamId={overview.teamId}
            />
        );
    }

    return (
        <section className="flex w-full max-w-md flex-col gap-4">
            {/* A compact identity card: the hierarchy (tournament, team, players)
                carries the meaning visually; the labels stay for screen readers.
                The team name doubles as the page heading. */}
            <dl className="flex flex-col gap-0.5 rounded-md border border-border bg-card px-4 py-3 break-words">
                <dt className="sr-only">{t('teamOverview.tournament')}</dt>
                <dd className="text-xs text-muted-foreground">{overview.tournamentName}</dd>
                <dt className="sr-only">{t('teamOverview.teamName')}</dt>
                <dd className="flex flex-wrap items-center gap-2">
                    <h1 className="text-lg font-semibold">{overview.teamName}</h1>
                    {live ? (
                        <LiveBadge connected={connected} labelKeyPrefix="team.live" />
                    ) : null}
                </dd>
                <dt className="sr-only">{t('teamOverview.players')}</dt>
                <dd className="text-sm text-muted-foreground">
                    <span>{playerOne?.name}</span>
                    <span aria-hidden="true"> · </span>
                    <span>{playerTwo?.name}</span>
                </dd>
            </dl>
            {/* Exactly one of: the schedule note (setup), the confirmation panel
                with its scoring link (started; read-only once no longer active),
                or the Final Ranking (published). The team's own table stays
                reachable through the scoring link in every started state. While
                the team is not seated the panel shrinks to the scoring link; the
                scoring screen still guards itself (401 → login, not seated →
                placeholder). */}
            {overview.tournamentStatus === TournamentStatus.SETUP ? (
                <p className="text-sm text-muted-foreground">{t('teamOverview.scheduleNote')}</p>
            ) : published ? (
                <>
                    {renderRanking()}
                    <ScoringLink primary={false} readOnly />
                </>
            ) : (
                <>
                    {overview.tournamentStatus === TournamentStatus.COMPLETED ? (
                        <p className="text-sm text-muted-foreground">
                            {t('teamOverview.completedNote')}
                        </p>
                    ) : null}
                    <TableConfirmationPanel
                        viewerTeamId={overview.teamId}
                        readOnly={overview.tournamentStatus !== TournamentStatus.ACTIVE}
                    />
                </>
            )}
        </section>
    );
}
