import { cn } from '@tichu/ui/lib/utils.ts';

import { useI18n } from '@/shared/i18n/i18n.context.tsx';

export interface LiveBadgeProps {
    connected: boolean;
    /** Catalog prefix of the three labels: `<prefix>.connected`, `<prefix>.offline`, `<prefix>.hint`. */
    labelKeyPrefix: 'tournament.live' | 'team.live' | 'public.live';
}

/**
 * Whether the live stream is connected: a pulsing red dot while connected, a static grey dot
 * while offline or reconnecting. The label stays in the DOM for screen readers.
 */
export function LiveBadge({ connected, labelKeyPrefix }: LiveBadgeProps) {
    const { t } = useI18n();

    return (
        <span
            title={t(`${labelKeyPrefix}.hint`)}
            className="relative inline-flex size-2.5 align-middle"
        >
            {connected ? (
                <span
                    aria-hidden
                    className="absolute inline-flex size-full animate-ping rounded-full bg-destructive opacity-75 motion-reduce:animate-none"
                />
            ) : null}
            <span
                aria-hidden
                className={cn(
                    'relative inline-flex size-2.5 rounded-full',
                    connected ? 'bg-destructive' : 'bg-muted-foreground',
                )}
            />
            <span className="sr-only">
                {connected ? t(`${labelKeyPrefix}.connected`) : t(`${labelKeyPrefix}.offline`)}
            </span>
        </span>
    );
}
