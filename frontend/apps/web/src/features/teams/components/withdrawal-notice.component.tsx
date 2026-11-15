import { useI18n } from '@/shared/i18n/i18n.context.tsx';
import { WithdrawalReason } from '@/vocabulary/withdrawal-reason.enum.ts';

/**
 * The notice a withdrawn team sees at the top of its pages (ADR 0011): a disqualified
 * team is told so; a team that withdrew on its own is told so and that it can still
 * view its games. Its tournament pages are read-only from then on.
 */
export function WithdrawalNotice({ reason }: { reason: WithdrawalReason }) {
    const { t } = useI18n();
    const disqualified = reason === WithdrawalReason.DISQUALIFIED;
    return (
        <div className="flex flex-col gap-1 rounded-md bg-muted px-3 py-2 text-sm">
            <p className="font-medium">
                {t(
                    disqualified
                        ? 'teamOverview.withdrawn.disqualified'
                        : 'teamOverview.withdrawn.voluntary',
                )}
            </p>
            {disqualified ? null : <p>{t('teamOverview.withdrawn.viewGames')}</p>}
        </div>
    );
}
