import type { TranslationKey } from '@/shared/i18n/catalogs/en.ts';
import { AdminRole, AdminStatus, InviteStatus } from '@/vocabulary/admin.enum.ts';

/** Maps each lifecycle value to its localized label, exhaustively over the vocabulary. */
export const ADMIN_STATUS_LABEL_KEYS: Record<AdminStatus, TranslationKey> = {
    [AdminStatus.PENDING]: 'ownerConsole.status.pending',
    [AdminStatus.ACTIVE]: 'ownerConsole.status.active',
    [AdminStatus.DISABLED]: 'ownerConsole.status.disabled',
};

export const ADMIN_ROLE_LABEL_KEYS: Record<AdminRole, TranslationKey> = {
    [AdminRole.OWNER]: 'ownerConsole.role.owner',
    [AdminRole.ADMIN]: 'ownerConsole.role.admin',
};

export const INVITE_STATUS_LABEL_KEYS: Record<InviteStatus, TranslationKey> = {
    [InviteStatus.PENDING]: 'ownerConsole.inviteStatus.pending',
    [InviteStatus.SPENT]: 'ownerConsole.inviteStatus.spent',
    [InviteStatus.REVOKED]: 'ownerConsole.inviteStatus.revoked',
};
