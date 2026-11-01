import { Link, createFileRoute } from '@tanstack/react-router';
import { Badge } from '@tichu/ui/badge.tsx';
import { buttonVariants } from '@tichu/ui/button.tsx';
import { TabList, TabPanel } from '@tichu/ui/tabs.tsx';

import { TeamsSection } from '@/features/teams/components/teams-section.component.tsx';
import { RoundsSection } from '@/features/tournaments/components/rounds-section.component.tsx';
import { useRounds } from '@/features/tournaments/hooks/start-rounds-queries.hook.ts';
import { StartTournamentButton } from '@/features/tournaments/components/start-tournament-button.component.tsx';
import { useTournament } from '@/features/tournaments/hooks/tournament-queries.hook.ts';
import { useTeams } from '@/features/teams/hooks/team-queries.hook.ts';
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

/** The two sections of a started tournament, switched by tabs and kept in the URL. */
type DetailTab = 'rounds' | 'teams';

interface DetailSearch {
    tab?: DetailTab;
}

/**
 * The tournament detail screen: identity and status, the start and edit entries
 * (edit only while in setup, since editing is locked afterwards), and the content
 * below. In setup that is the team list alone; once started, Rounds and Teams sit
 * behind tabs — Rounds first, since that is what an admin follows during play.
 * The active tab lives in `?tab=` so a reload or the back button keeps it.
 */
function TournamentDetailComponent() {
        const { t } = useI18n();
        const { tournamentId } = Route.useParams();
        const tournamentQuery = useTournament(tournamentId);
        const teamsQuery = useTeams(tournamentId);
        const { tab = 'rounds' } = Route.useSearch();
        const navigate = Route.useNavigate();
        const status = tournamentQuery.data?.status;
        const live = status === TournamentStatus.ACTIVE;
        // Shares the rounds section's query (same key and polling), only for the tab count.
        const roundsQuery = useRounds(tournamentId, {
            live,
            enabled: status !== undefined && status !== TournamentStatus.SETUP,
        });
        const roundCount = roundsQuery.data?.length;

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
                                    label: roundCount === undefined
                                        ? t('rounds.section.title')
                                        : `${t('rounds.section.title')} (${roundCount})`,
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
                                    tournamentId={tournament.id}
                                    teams={teams}
                                    live={live}
                                />
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
        search.tab === 'rounds' || search.tab === 'teams' ? { tab: search.tab } : {},
    component: TournamentDetailComponent,
});
