import { StatisticsPanel } from '@/shared/components/statistics-panel.component.tsx';
import { useStatistics } from '@/features/statistics/hooks/statistics-queries.hook.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';

interface StatisticsSectionProps {
    tournamentId: string;
}

/**
 * The admin's Statistics tab (glossary: Statistics). Fetches the organizer's statistics and
 * hands them to the shared panel unchanged, so it computes nothing itself.
 */
export function StatisticsSection({ tournamentId }: StatisticsSectionProps) {
    const { t } = useI18n();
    const statisticsQuery = useStatistics(tournamentId);

    return (
        <section aria-label={t('statistics.title')} className="flex flex-col gap-3">
            <StatisticsPanel query={statisticsQuery} />
        </section>
    );
}
