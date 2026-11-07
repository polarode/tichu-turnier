import type { PublicTournament } from '@/domain/tournament.domain.ts';
import { usePublicTournaments } from '@/features/public-tournaments/hooks/public-tournaments.hook.ts';
import { TournamentStatusBadge } from '@/features/tournaments/components/tournament-status-badge.component.tsx';
import type { TranslationKey } from '@/shared/i18n/catalogs/en.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';
import { TournamentStatus } from '@/vocabulary/tournament-status.enum.ts';

/** The groups of the home page, in display order; each reuses the tournament status label. */
const GROUPS: readonly { status: TournamentStatus; labelKey: TranslationKey }[] = [
    { status: TournamentStatus.ACTIVE, labelKey: 'tournament.status.active' },
    { status: TournamentStatus.COMPLETED, labelKey: 'tournament.status.completed' },
    { status: TournamentStatus.PUBLISHED, labelKey: 'tournament.status.published' },
];

/**
 * The public home: the public tournaments grouped by status, read-only. Entries are not
 * links because there is no public tournament detail page yet.
 */
export function PublicHome() {
    const { t } = useI18n();
    const publicTournamentsQuery = usePublicTournaments();
    const tournaments = publicTournamentsQuery.data ?? [];

    return (
        <section className="flex flex-col gap-6">
            <div className="flex flex-col gap-1">
                <h1 className="text-2xl font-semibold">{t('publicHome.title')}</h1>
                <p className="text-muted-foreground">{t('publicHome.description')}</p>
            </div>

            {publicTournamentsQuery.isPending ? (
                <p className="text-muted-foreground">{t('common.loading')}</p>
            ) : publicTournamentsQuery.isError ? (
                <p role="alert" className="text-sm text-destructive">
                    {t('publicHome.error')}
                </p>
            ) : tournaments.length === 0 ? (
                <div className="flex flex-col items-start gap-2 rounded-md border border-dashed border-border p-6">
                    <p className="text-muted-foreground">{t('publicHome.empty')}</p>
                </div>
            ) : (
                GROUPS.map((group) => {
                    const entries = tournaments.filter(
                        (tournament) => tournament.status === group.status,
                    );
                    if (entries.length === 0) return null;
                    return (
                        <PublicGroup
                            key={group.status}
                            label={t(group.labelKey)}
                            entries={entries}
                        />
                    );
                })
            )}
        </section>
    );
}

function PublicGroup({ label, entries }: { label: string; entries: PublicTournament[] }) {
    const { t } = useI18n();
    return (
        <section className="flex flex-col gap-3">
            <h2 className="text-lg font-medium">{label}</h2>
            <ul className="flex flex-col gap-3">
                {entries.map((tournament) => (
                    <li
                        key={tournament.id}
                        className="flex flex-wrap items-center justify-between gap-4 rounded-md border border-border bg-card p-4"
                    >
                        <div className="flex flex-col gap-1">
                            <span className="font-medium">{tournament.name}</span>
                            <div className="flex items-center gap-3 text-sm text-muted-foreground">
                                <TournamentStatusBadge status={tournament.status} />
                                <span>{`${t('publicHome.teamCount')}: ${tournament.teamCount}`}</span>
                            </div>
                        </div>
                    </li>
                ))}
            </ul>
        </section>
    );
}
