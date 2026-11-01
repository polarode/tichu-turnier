import { Badge } from '@tichu/ui/badge.tsx';

import type { TranslationKey } from '@/shared/i18n/catalogs/en.ts';
import { useI18n } from '@/shared/i18n/i18n.context.tsx';
import { TournamentStatus } from '@/vocabulary/tournament-status.enum.ts';

const STATUS_LABEL_KEYS: Record<TournamentStatus, TranslationKey> = {
    [TournamentStatus.SETUP]: 'tournament.status.setup',
    [TournamentStatus.ACTIVE]: 'tournament.status.active',
    [TournamentStatus.COMPLETED]: 'tournament.status.completed',
};

/** Setup is neutral, a running tournament is green, a finished one takes the "done" tint. */
const STATUS_BADGE_VARIANTS: Record<TournamentStatus, 'muted' | 'success' | 'secondary'> = {
    [TournamentStatus.SETUP]: 'muted',
    [TournamentStatus.ACTIVE]: 'success',
    [TournamentStatus.COMPLETED]: 'secondary',
};

/** The tournament's lifecycle status as a labelled, coloured badge. */
export function TournamentStatusBadge({ status }: { status: TournamentStatus }) {
    const { t } = useI18n();
    return <Badge variant={STATUS_BADGE_VARIANTS[status]}>{t(STATUS_LABEL_KEYS[status])}</Badge>;
}
