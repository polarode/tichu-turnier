import { Check, Circle } from 'lucide-react';
import { cn } from '@tichu/ui/lib/utils.ts';

import { seatTone } from '@/shared/constants/seat-tones.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';

/**
 * A seated team's confirmation of its table (ADR 0006): filled in the team's seat
 * colour with a check once it confirmed, a dashed outline with an empty circle in
 * that colour while it has not. The state is spelled out for screen readers
 * ("Team Beta, pending"). Inline, so it also fits inside a button; `sm` suits a
 * dense list row.
 */
export function ConfirmationPill({
    name,
    seat,
    confirmed,
    size = 'md',
}: {
    name: string;
    /** The team's 0-based seat at its table, which picks its colour. */
    seat: number;
    confirmed: boolean;
    size?: 'sm' | 'md';
}) {
    const { t } = useI18n();
    const tone = seatTone(seat);
    const Icon = confirmed ? Check : Circle;
    return (
        <span
            className={cn(
                'inline-flex max-w-full min-w-0 items-center gap-1.5 rounded-full border font-medium',
                size === 'sm' ? 'px-2 py-0.5 text-xs' : 'px-2.5 py-1',
                confirmed
                    ? cn('border-transparent', tone.chip)
                    : 'border-dashed border-input text-muted-foreground',
            )}
        >
            <Icon
                aria-hidden="true"
                className={cn(
                    'shrink-0',
                    size === 'sm' ? 'size-3.5' : 'size-4',
                    !confirmed && cn('scale-90', tone.text),
                )}
            />
            <span className="truncate">{name}</span>
            <span className="sr-only">
                {`, ${t(confirmed ? 'confirmation.teamConfirmed' : 'confirmation.teamPending')}`}
            </span>
        </span>
    );
}
