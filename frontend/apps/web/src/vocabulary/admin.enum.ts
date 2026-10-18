/**
 * Vocabulary for admin accounts and invites. Mirrors the backend enums so the domain
 * model, the DTO schemas and the UI all reference one source of truth.
 */

/** Account lifecycle: registration starts `PENDING`; an owner approval or invite activates it. */
export const AdminStatus = {
    PENDING: 'PENDING',
    ACTIVE: 'ACTIVE',
    DISABLED: 'DISABLED',
} as const;

export type AdminStatus = (typeof AdminStatus)[keyof typeof AdminStatus];

export const ADMIN_STATUSES = [
    AdminStatus.PENDING,
    AdminStatus.ACTIVE,
    AdminStatus.DISABLED,
] as const;

/** Authority level. `OWNER` is provisioned from configuration only; registration yields `ADMIN`. */
export const AdminRole = {
    OWNER: 'OWNER',
    ADMIN: 'ADMIN',
} as const;

export type AdminRole = (typeof AdminRole)[keyof typeof AdminRole];

export const ADMIN_ROLES = [AdminRole.OWNER, AdminRole.ADMIN] as const;

/** Invite lifecycle: `PENDING` until accepted (`SPENT`) or revoked (`REVOKED`). */
export const InviteStatus = {
    PENDING: 'PENDING',
    SPENT: 'SPENT',
    REVOKED: 'REVOKED',
} as const;

export type InviteStatus = (typeof InviteStatus)[keyof typeof InviteStatus];

export const INVITE_STATUSES = [
    InviteStatus.PENDING,
    InviteStatus.SPENT,
    InviteStatus.REVOKED,
] as const;
