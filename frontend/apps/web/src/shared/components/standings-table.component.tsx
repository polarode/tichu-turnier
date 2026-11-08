import { DataTable, type DataTableColumn } from '@tichu/ui/data-table.tsx';

import type { StandingsEntry } from '@/domain/standings.domain.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';

interface StandingsTableProps {
    /** One line per team, in the server's order: that is the default order. Ranks are rendered verbatim. */
    entries: StandingsEntry[];
    /** Accessible name of the table, shown as its (visually hidden) caption. */
    caption: string;
    /** The viewer's own team: its row is tinted and marked `aria-current`. */
    highlightTeamId?: string;
}

/**
 * The presentational standings table shared by the admin Standings tab, the
 * team's Final Ranking (S7c, #116) and the public tournament page. It renders the
 * entries it is given and computes nothing, so every screen shows the same markup.
 * The server order is the default order; viewers can re-sort by any column, and the
 * rank column always shows each team's real rank, shared ranks included.
 */
export function StandingsTable({ entries, caption, highlightTeamId }: StandingsTableProps) {
    const { t, locale } = useI18n();
    const columns: DataTableColumn<StandingsEntry>[] = [
        {
            id: 'rank',
            header: t('standings.rank'),
            cell: (entry) => entry.rank,
            sortValue: (entry) => entry.rank,
            sortFirst: 'asc',
        },
        {
            id: 'team',
            header: t('standings.team'),
            cell: (entry) => entry.teamName,
            sortValue: (entry) => entry.teamName,
            sortFirst: 'asc',
            rowHeader: true,
            cellClassName: 'font-medium',
        },
        {
            id: 'victoryPoints',
            header: (
                <abbr title={t('standings.victoryPoints.long')} className="no-underline">
                    {t('standings.victoryPoints')}
                </abbr>
            ),
            cell: (entry) => entry.victoryPoints,
            sortValue: (entry) => entry.victoryPoints,
            align: 'end',
            cellClassName: 'font-medium',
        },
        {
            id: 'tichuPoints',
            header: (
                <abbr title={t('standings.tichuPoints.long')} className="no-underline">
                    {t('standings.tichuPoints')}
                </abbr>
            ),
            cell: (entry) => entry.tichuPoints,
            sortValue: (entry) => entry.tichuPoints,
            align: 'end',
        },
    ];

    return (
        <div className="rounded-md border border-border bg-card">
            <DataTable
                className="text-sm tabular-nums"
                columns={columns}
                rows={entries}
                getRowId={(entry) => entry.teamId}
                caption={caption}
                locale={locale}
                rowProps={(entry) => {
                    const own = entry.teamId === highlightTeamId;
                    return own
                        ? {
                              'aria-current': 'true',
                              className: 'bg-secondary font-semibold text-secondary-foreground',
                          }
                        : {};
                }}
            />
        </div>
    );
}
