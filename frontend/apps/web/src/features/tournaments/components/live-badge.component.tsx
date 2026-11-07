import { Badge } from '@tichu/ui/badge.tsx';

import { useI18n } from '@/shared/i18n/i18n.context.tsx';

/** Whether the live stream is connected: green "Live", or a muted "Offline" while it reconnects. */
export function LiveBadge({ connected }: { connected: boolean }) {
    const { t } = useI18n();
    return (
        <Badge variant={connected ? 'success' : 'muted'} title={t('tournament.live.hint')}>
            {t(connected ? 'tournament.live.connected' : 'tournament.live.offline')}
        </Badge>
    );
}
