import { Link } from '@tanstack/react-router';

import type { Tournament } from '@/domain/tournament.domain.ts';
import { ConsoleRow } from '@/features/owner-console/components/console-row.component.tsx';
import { ConsoleSection } from '@/features/owner-console/components/console-section.component.tsx';
import { groupTournamentsByLeadOrganizer } from '@/features/owner-console/tournaments-by-lead-organizer.ts';
import { TournamentStatusBadge } from '@/features/tournaments/components/tournament-status-badge.component.tsx';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';

/**
 * Every tournament on the platform, one group per lead organizer. Each row links to the
 * tournament's admin page, so the owner can open any tournament from here.
 */
export function AllTournaments({ tournaments }: { tournaments: Tournament[] }) {
    const { t } = useI18n();
    const groups = groupTournamentsByLeadOrganizer(tournaments);

    return (
        <ConsoleSection
            title={t('ownerConsole.tournaments.title')}
            description={t('ownerConsole.tournaments.description')}
        >
            {groups.length === 0 ? (
                <p className="text-sm text-muted-foreground">
                    {t('ownerConsole.tournaments.empty')}
                </p>
            ) : (
                <div className="flex flex-col gap-5">
                    {groups.map((group) => (
                        <div key={group.email} className="flex flex-col gap-2">
                            <h3 className="text-sm font-semibold text-muted-foreground">
                                {group.email}
                            </h3>
                            <ul className="flex flex-col gap-2">
                                {group.tournaments.map((tournament) => (
                                    <ConsoleRow key={tournament.id}>
                                        <div className="flex flex-wrap items-center gap-3">
                                            <Link
                                                to="/admin/tournaments/$tournamentId"
                                                params={{ tournamentId: tournament.id }}
                                                className="font-medium hover:underline"
                                            >
                                                {tournament.name}
                                            </Link>
                                            <TournamentStatusBadge status={tournament.status} />
                                        </div>
                                    </ConsoleRow>
                                ))}
                            </ul>
                        </div>
                    ))}
                </div>
            )}
        </ConsoleSection>
    );
}
