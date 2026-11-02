import type { TableConfirmation } from '@/domain/game.domain.ts';
import type { TranslationKey } from '@/shared/i18n/catalogs/en.ts';
import type { I18n } from '@/shared/i18n/i18n.context.tsx';
import { TableStatus } from '@/vocabulary/table-status.enum.ts';

/** The table status label, shared by the team's confirmation panel and the admin rounds view. */
export const TABLE_STATUS_LABEL_KEYS: Record<TableStatus, TranslationKey> = {
    [TableStatus.PLAYING]: 'confirmation.status.playing',
    [TableStatus.CONFIRMING]: 'confirmation.status.confirming',
    [TableStatus.COMPLETED]: 'confirmation.status.completed',
};

/** "k of n confirmed" for a table seating [seatedTeams] teams, worded alike in both views. */
export function confirmedCountLabel(confirmation: TableConfirmation, seatedTeams: number, t: I18n['t']): string {
    return `${confirmation.confirmedTeamIds.length} ${t('confirmation.count.of')} ${seatedTeams} ${t('confirmation.count.confirmed')}`;
}
