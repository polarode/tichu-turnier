import type { AdminRole, AdminStatus, InviteStatus } from '@/vocabulary/admin.enum.ts';

/**
 * An admin account as the owner console knows it. A projection of the backend's
 * `AdminAccountView`: it never carries password or token material, and timestamps are
 * real `Date`s here where the DTO holds ISO strings.
 */
export interface AdminAccount {
    id: string;
    email: string;
    status: AdminStatus;
    role: AdminRole;
    createdAt: Date;
    /** The owner who approved this account, when it was approved rather than invited. */
    approvedBy: string | null;
    approvedAt: Date | null;
    /** The owner who invited this account, when an invite activated it. */
    invitedBy: string | null;
    invitedAt: Date | null;
}

/** An owner-issued admin invitation. Never carries the secret token, only its lifecycle. */
export interface AdminInvite {
    id: string;
    email: string;
    status: InviteStatus;
    createdBy: string;
    createdAt: Date;
    expiresAt: Date;
    spentAt: Date | null;
}

/**
 * The one shape that carries the raw invite secret: returned exactly once, at issuance.
 * [link] is the copyable URL the owner hands to the invitee.
 */
export interface IssuedInvite extends AdminInvite {
    token: string;
    link: string;
}
