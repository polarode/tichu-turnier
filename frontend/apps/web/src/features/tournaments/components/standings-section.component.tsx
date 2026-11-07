import { StandingsTable } from '@/shared/components/standings-table.component.tsx';
import { useStandings } from '@/features/tournaments/hooks/standings-queries.hook.ts';
import { LoadingIndicator } from '@/shared/components/loading-indicator.component.tsx';
import { localizedErrorMessage } from '@/shared/forms/server-error.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';

interface StandingsSectionProps {
    tournamentId: string;
    /** The tournament is COMPLETED or PUBLISHED: the table is the Final Ranking. */
    final?: boolean;
}

/**
 * The admin's Standings tab (glossary: Standings). Renders the server's order and
 * ranks verbatim — tied teams show the same rank — and computes nothing itself, so
 * it cannot drift from the backend. Until any table is completed every team would
 * sit at zero, so a note stands in for that table of zeros.
 */
export function StandingsSection({ tournamentId, final = false }: StandingsSectionProps) {
    const { t } = useI18n();
    const title = t(final ? 'standings.finalTitle' : 'standings.title');
    const standingsQuery = useStandings(tournamentId);

    const body = (() => {
        if (standingsQuery.isPending) return <LoadingIndicator />;
        if (standingsQuery.isError) {
            return (
                <p role="alert" className="text-sm text-destructive">
                    {localizedErrorMessage(standingsQuery.error, t)}
                </p>
            );
        }
        const entries = standingsQuery.data;
        if (entries.every((entry) => entry.gamesPlayed === 0)) {
            return <p className="text-sm text-muted-foreground">{t('standings.empty')}</p>;
        }
        return <StandingsTable entries={entries} caption={title} />;
    })();

    return (
        <section aria-label={title} className="flex flex-col gap-3">
            {body}
        </section>
    );
}
