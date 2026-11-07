import type { StandingsEntry } from '@/domain/standings.domain.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';

interface StandingsTableProps {
    /** One line per team, in the server's order; ranks are rendered verbatim. */
    entries: StandingsEntry[];
    /** Accessible name of the table, shown as its (visually hidden) caption. */
    caption: string;
    /** The viewer's own team: its row is tinted and marked `aria-current`. */
    highlightTeamId?: string;
}

/**
 * The presentational standings table shared by the admin Standings tab and the
 * team's Final Ranking (S7c, #116). It renders the entries it is given and computes
 * nothing, so the admin and the team see the same markup and the same order.
 */
export function StandingsTable({ entries, caption, highlightTeamId }: StandingsTableProps) {
    const { t } = useI18n();
    return (
        <div className="overflow-x-auto rounded-md border border-border bg-card">
            <table className="w-full text-sm tabular-nums">
                <caption className="sr-only">{caption}</caption>
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
                            <abbr title={t('standings.tichuPoints.long')} className="no-underline">
                                {t('standings.tichuPoints')}
                            </abbr>
                        </th>
                    </tr>
                </thead>
                <tbody className="divide-y divide-border">
                    {entries.map((entry) => {
                        const own = entry.teamId === highlightTeamId;
                        return (
                            <tr
                                key={entry.teamId}
                                aria-current={own ? 'true' : undefined}
                                className={
                                    own
                                        ? 'bg-secondary font-semibold text-secondary-foreground'
                                        : undefined
                                }
                            >
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
                        );
                    })}
                </tbody>
            </table>
        </div>
    );
}
