import type { TranslationKey } from '@/shared/i18n/catalogs/en.ts';
import { TableStatus } from '@/vocabulary/table-status.enum.ts';

/** The table status label, shared by the team's confirmation panel and the admin rounds view. */
export const TABLE_STATUS_LABEL_KEYS: Record<TableStatus, TranslationKey> = {
    [TableStatus.PLAYING]: 'confirmation.status.playing',
    [TableStatus.CONFIRMING]: 'confirmation.status.confirming',
    [TableStatus.COMPLETED]: 'confirmation.status.completed',
};
