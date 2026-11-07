import { Badge } from '@tichu/ui/badge.tsx';

import { useI18n } from '@/shared/i18n/i18n.context.tsx';

export interface LiveBadgeProps {
    connected: boolean;
    /** Catalog prefix of the three labels: `<prefix>.connected`, `<prefix>.offline`, `<prefix>.hint`. */
    labelKeyPrefix: 'tournament.live' | 'team.live';
}

/** Whether the live stream is connected: green "Live", or a muted "Offline" while it reconnects. */
export function LiveBadge({ connected, labelKeyPrefix }: LiveBadgeProps) {
    const { t } = useI18n();

    return (
        <Badge variant={connected ? 'success' : 'muted'} title={t(`${labelKeyPrefix}.hint`)}>
            {connected ? t(`${labelKeyPrefix}.connected`) : t(`${labelKeyPrefix}.offline`)}
        </Badge>
    );
}
