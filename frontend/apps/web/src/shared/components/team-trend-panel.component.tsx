import type { UseQueryResult } from '@tanstack/react-query';

import type { TournamentStatistics } from '@/domain/statistics.domain.ts';
import { LoadingIndicator } from '@/shared/components/loading-indicator.component.tsx';
import { TeamTrend } from '@/shared/components/team-trend.component.tsx';
import { localizedErrorMessage } from '@/shared/forms/server-error.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';

/**
 * The Team Trend under a ranking, with the statistics read's loading and error states.
 * The organizer's standings tab and the public ranking tab each pass their own query.
 */
export function TeamTrendPanel({ query }: { query: UseQueryResult<TournamentStatistics> }) {
    const { t } = useI18n();

    if (query.isPending) return <LoadingIndicator />;
    if (query.isError) {
        return (
            <p role="alert" className="text-sm text-destructive">
                {localizedErrorMessage(query.error, t)}
            </p>
        );
    }
    return <TeamTrend trends={query.data.trends} />;
}
