import { Badge } from '@tichu/ui/badge.tsx';

import { useI18n } from '@/shared/i18n/i18n.context.tsx';
import { WithdrawalReason } from '@/vocabulary/withdrawal-reason.enum.ts';

interface WithdrawalBadgeProps {
    reason: WithdrawalReason;
    className?: string;
}

/**
 * The badge naming why a team withdrew (ADR 0011): a disqualified team reads destructive,
 * a voluntarily withdrawn one muted. The label carries the reason, so the tone is never the
 * only signal.
 */
export function WithdrawalBadge({ reason, className }: WithdrawalBadgeProps) {
    const { t } = useI18n();
    const disqualified = reason === WithdrawalReason.DISQUALIFIED;
    return (
        <Badge variant={disqualified ? 'destructive' : 'muted'} className={className}>
            {t(disqualified ? 'withdrawal.reason.DISQUALIFIED' : 'withdrawal.reason.VOLUNTARY')}
        </Badge>
    );
}
