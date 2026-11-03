import { useStandings } from '@/features/tournaments/hooks/standings-queries.hook.ts';
import { LoadingIndicator } from '@/shared/components/loading-indicator.component.tsx';
import { localizedErrorMessage } from '@/shared/forms/server-error.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';

interface StandingsSectionProps {
    tournamentId: string;
    /** The tournament is ACTIVE: tables keep completing, so the standings poll. */
    live?: boolean;
}

/**
 * The admin's Standings tab (glossary: Standings). Renders the server's order and
 * ranks verbatim — tied teams show the same rank — and computes nothing itself, so
 * it cannot drift from the backend. Until any table is completed every team would
 * sit at zero, so a note stands in for that table of zeros.
 */
export function StandingsSection({ tournamentId, live = false }: StandingsSectionProps) {
    const { t } = useI18n();
    const standingsQuery = useStandings(tournamentId, { live });

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
        return (
            <div className="overflow-x-auto rounded-md border border-border bg-card">
                <table className="w-full text-sm tabular-nums">
                    <caption className="sr-only">{t('standings.title')}</caption>
                    <thead>
                        <tr className="border-b border-border text-left text-muted-foreground">
                            <th scope="col" className="px-3 py-2 font-medium">
                                {t('standings.rank')}
                            </th>
                            <th scope="col" className="px-3 py-2 font-medium">
                                {t('standings.team')}
                            </th>
                            <th scope="col" className="px-3 py-2 text-right font-medium">
                                {t('standings.games')}
                            </th>
                            <th scope="col" className="px-3 py-2 text-right font-medium">
                                <abbr
                                    title={t('standings.victoryPoints.long')}
                                    className="no-underline"
                                >
                                    {t('standings.victoryPoints')}
                                </abbr>
                            </th>
                            <th scope="col" className="px-3 py-2 text-right font-medium">
                                <abbr
                                    title={t('standings.tichuPoints.long')}
                                    className="no-underline"
                                >
                                    {t('standings.tichuPoints')}
                                </abbr>
                            </th>
                        </tr>
                    </thead>
                    <tbody className="divide-y divide-border">
                        {entries.map((entry) => (
                            <tr key={entry.teamId}>
                                <td className="px-3 py-2">{entry.rank}</td>
                                <th scope="row" className="px-3 py-2 text-left font-medium">
                                    {entry.teamName}
                                </th>
                                <td className="px-3 py-2 text-right">{entry.gamesPlayed}</td>
                                <td className="px-3 py-2 text-right font-medium">
                                    {entry.victoryPoints}
                                </td>
                                <td className="px-3 py-2 text-right">{entry.tichuPoints}</td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>
        );
    })();

    return (
        <section aria-label={t('standings.title')} className="flex flex-col gap-3">
            {body}
        </section>
    );
}
