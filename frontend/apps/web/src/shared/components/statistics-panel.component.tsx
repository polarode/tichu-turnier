import type { UseQueryResult } from '@tanstack/react-query';

import type { TournamentStatistics } from '@/domain/statistics.domain.ts';
import { LoadingIndicator } from '@/shared/components/loading-indicator.component.tsx';
import { StatisticsView } from '@/shared/components/statistics-view.component.tsx';
import { localizedErrorMessage } from '@/shared/forms/server-error.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';

interface StatisticsPanelProps {
    /** The statistics read of the caller's audience (organizer or spectator). */
    query: UseQueryResult<TournamentStatistics>;
}

/**
 * One statistics query's states: loading, the localized error, or the shared
 * [StatisticsView]. Each surface owns its query and its surrounding section; this is the
 * part they all share.
 */
export function StatisticsPanel({ query }: StatisticsPanelProps) {
    const { t } = useI18n();

    if (query.isPending) return <LoadingIndicator />;
    if (query.isError) {
        return (
            <p role="alert" className="text-sm text-destructive">
                {localizedErrorMessage(query.error, t)}
            </p>
        );
    }
    return <StatisticsView statistics={query.data} />;
}
