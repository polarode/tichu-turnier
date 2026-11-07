import { useState } from 'react';
import { Link, createFileRoute } from '@tanstack/react-router';
import { buttonVariants } from '@tichu/ui/button.tsx';
import { TabList, TabPanel } from '@tichu/ui/tabs.tsx';

import { TeamsSection } from '@/features/teams/components/teams-section.component.tsx';
import { TournamentStatusBadge } from '@/features/tournaments/components/tournament-status-badge.component.tsx';
import { ProgressCard } from '@/features/tournaments/components/progress-card.component.tsx';
import { RoundsSection } from '@/features/tournaments/components/rounds-section.component.tsx';
import { StandingsSection } from '@/features/tournaments/components/standings-section.component.tsx';
import { useRounds } from '@/features/tournaments/hooks/start-rounds-queries.hook.ts';
import { StartTournamentButton } from '@/features/tournaments/components/start-tournament-button.component.tsx';
import { useTournamentLiveStream } from '@/features/tournaments/hooks/tournament-live-stream.hook.ts';
import { useTournament } from '@/features/tournaments/hooks/tournament-queries.hook.ts';
import { useTeams } from '@/features/teams/hooks/team-queries.hook.ts';
import { BackLink } from '@/shared/components/back-link.component.tsx';
import { LiveBadge } from '@/shared/components/live-badge.component.tsx';
import { PageHeader } from '@/shared/components/page-header.component.tsx';
import { localizedErrorMessage } from '@/shared/forms/server-error.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';
import { TournamentStatus } from '@/vocabulary/tournament-status.enum.ts';

/** The sections of a started tournament, switched by tabs and kept in the URL. */
type DetailTab = 'rounds' | 'standings' | 'teams';

const DETAIL_TABS: readonly DetailTab[] = ['rounds', 'standings', 'teams'];

function isDetailTab(value: unknown): value is DetailTab {
    return DETAIL_TABS.some((tab) => tab === value);
}

interface DetailSearch {
    tab?: DetailTab;
}

/**
 * The tournament detail screen: identity and status, the start and edit entries
 * (edit only while in setup, since editing is locked afterwards), and the content
 * below. In setup that is the team list alone; once started, a progress card
 * (round x of the Total Rounds, the current round's completed tables) sits above
 * Rounds · Standings · Teams tabs — Rounds first, since that is what an admin
 * follows during play. The active tab lives in `?tab=` so a reload or the back
 * button keeps it.
 */
function TournamentDetailComponent() {
    // Outside the body, so loading and error states keep a way back too.
    return (
        <div className="flex flex-col gap-4">
            <BackLink to="/admin" labelKey="dashboard.backLink" />
            <TournamentDetailBody />
        </div>
    );
}

function TournamentDetailBody() {
    const { t } = useI18n();
    const { tournamentId } = Route.useParams();
    const tournamentQuery = useTournament(tournamentId);
    const teamsQuery = useTeams(tournamentId);
    const { tab = 'rounds' } = Route.useSearch();
    const navigate = Route.useNavigate();
    const status = tournamentQuery.data?.status;
    const live = status === TournamentStatus.ACTIVE;
    const connected = useTournamentLiveStream(tournamentId, live);
    // Shares the rounds section's query (same key), only for the tab count.
    const roundsQuery = useRounds(tournamentId, {
        enabled: status !== undefined && status !== TournamentStatus.SETUP,
    });
    const roundCount = roundsQuery.data?.length;
    // Bumped when a round starts: remounts the rounds view, which then follows the latest round.
    const [roundsViewKey, setRoundsViewKey] = useState(0);
    const showNewRound = () => {
        setRoundsViewKey((key) => key + 1);
        void navigate({ search: { tab: 'rounds' }, replace: true });
    };

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
    const teamsReady = !teamsQuery.isPending && !teamsQuery.isError;

    const tournament = tournamentQuery.data;
    const editable = tournament.status === TournamentStatus.SETUP;
    const isFinal =
        tournament.status === TournamentStatus.COMPLETED ||
        tournament.status === TournamentStatus.PUBLISHED;
    const teams = teamsQuery.data ?? [];

    return (
        <section className="flex flex-col gap-6">
            <div className="flex flex-wrap items-start justify-between gap-3">
                <PageHeader
                    titleKey="tournament.detail.title"
                    descriptionKey="tournament.detail.description"
                />
                <div className="flex items-center gap-3">
                    {teamsReady ? (
                        <StartTournamentButton tournament={tournament} teams={teams} />
                    ) : null}
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
            </div>

            <div className="flex flex-col gap-2 rounded-md border border-border bg-card p-4">
                <div className="flex items-center gap-3">
                    <span className="text-lg font-medium">{tournament.name}</span>
                    <TournamentStatusBadge status={tournament.status} />
                    {live ? (
                        <LiveBadge connected={connected} labelKeyPrefix="tournament.live" />
                    ) : null}
                </div>
                {tournament.description ? (
                    <p className="text-muted-foreground">{tournament.description}</p>
                ) : null}
                <p className="text-sm text-muted-foreground">
                    {`${t('tournament.form.maxTeams')}: ${tournament.maxTeams}`}
                </p>
                <p className="text-sm text-muted-foreground">
                    {`${t('tournament.form.grandTichuBonus')}: ${tournament.settings.grandTichuBonus}`}
                </p>
                <p className="text-sm text-muted-foreground">
                    {`${t('tournament.form.doubleWinBonus')}: ${tournament.settings.doubleWinBonus}`}
                </p>
                {editable ? (
                    // Once started, the progress card shows (and adjusts) the Total Rounds.
                    <p className="text-sm text-muted-foreground">
                        {`${t('tournament.form.totalRounds')}: ${tournament.settings.totalRounds}`}
                    </p>
                ) : null}
            </div>

            {!editable && roundsQuery.data ? (
                <ProgressCard
                    tournament={tournament}
                    rounds={roundsQuery.data}
                    onRoundStarted={showNewRound}
                />
            ) : null}

            {teamsQuery.isPending ? (
                <p className="text-muted-foreground">{t('common.loading')}</p>
            ) : teamsQuery.isError ? (
                <p role="alert" className="text-sm text-destructive">
                    {localizedErrorMessage(teamsQuery.error, t)}
                </p>
            ) : editable ? (
                // Setup has no rounds yet: the team list is the whole page, no tabs.
                <TeamsSection
                    tournamentId={tournament.id}
                    maxTeams={tournament.maxTeams}
                    editable={editable}
                    teams={teams}
                />
            ) : (
                <div className="flex flex-col gap-4">
                    <TabList
                        id="tournament-section"
                        variant="underline"
                        label={t('tournament.detail.sections')}
                        value={tab}
                        onValueChange={(next) =>
                            void navigate({ search: { tab: next }, replace: true })
                        }
                        items={[
                            {
                                value: 'rounds',
                                label:
                                    roundCount === undefined
                                        ? t('rounds.section.title')
                                        : `${t('rounds.section.title')} (${roundCount})`,
                            },
                            {
                                value: 'standings',
                                label: t(isFinal ? 'standings.finalTitle' : 'standings.title'),
                            },
                            {
                                value: 'teams',
                                label: `${t('teams.section.title')} (${teams.length})`,
                            },
                        ]}
                    />
                    <TabPanel id="tournament-section" value={tab}>
                        {tab === 'rounds' ? (
                            <RoundsSection
                                key={roundsViewKey}
                                tournamentId={tournament.id}
                                teams={teams}
                            />
                        ) : tab === 'standings' ? (
                            <StandingsSection tournamentId={tournament.id} final={isFinal} />
                        ) : (
                            <TeamsSection
                                tournamentId={tournament.id}
                                maxTeams={tournament.maxTeams}
                                editable={editable}
                                teams={teams}
                            />
                        )}
                    </TabPanel>
                </div>
            )}
        </section>
    );
}

export const Route = createFileRoute('/admin/tournaments/$tournamentId')({
    validateSearch: (search: Record<string, unknown>): DetailSearch =>
        isDetailTab(search.tab) ? { tab: search.tab } : {},
    component: TournamentDetailComponent,
});
