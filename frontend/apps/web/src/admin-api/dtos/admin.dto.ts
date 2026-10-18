import { z } from 'zod';

import {
    ADMIN_ROLES,
    ADMIN_STATUSES,
    INVITE_STATUSES,
} from '@/vocabulary/admin.enum.ts';

/**
 * zod schemas describing the owner-console wire format. Every response is parsed at the
 * repository boundary, so a contract drift fails loudly instead of leaking `any`
 * through the app. `approvedBy`/`invitedBy` are opaque owner ids, not credentials.
 */
export const adminAccountDtoSchema = z.object({
    id: z.string(),
    email: z.string(),
    status: z.enum(ADMIN_STATUSES),
    role: z.enum(ADMIN_ROLES),
    createdAt: z.string(),
    approvedBy: z.string().nullable(),
    approvedAt: z.string().nullable(),
    invitedBy: z.string().nullable(),
    invitedAt: z.string().nullable(),
});

export const adminAccountListDtoSchema = z.array(adminAccountDtoSchema);

export const inviteDtoSchema = z.object({
    id: z.string(),
    email: z.string(),
    status: z.enum(INVITE_STATUSES),
    createdBy: z.string(),
    createdAt: z.string(),
    expiresAt: z.string(),
    spentAt: z.string().nullable(),
});

export const inviteListDtoSchema = z.array(inviteDtoSchema);

/** The issuance response is the only one that adds the raw token and the copyable link. */
export const issuedInviteDtoSchema = inviteDtoSchema.extend({
    token: z.string(),
    link: z.string(),
});

export type AdminAccountDto = z.infer<typeof adminAccountDtoSchema>;
export type InviteDto = z.infer<typeof inviteDtoSchema>;
export type IssuedInviteDto = z.infer<typeof issuedInviteDtoSchema>;

/** Wire payload for issuing an invite. Validated at the server boundary too. */
export interface CreateInviteRequestDto {
    email: string;
}
