import type { AdminRole } from '@/vocabulary/admin.enum.ts';

/**
 * The authenticated organizer as the UI knows them. Deliberately minimal: the wire
 * response never carries password or token material, so neither does the domain model.
 * The [role] lets the UI show an owner-only console; it is never a security boundary —
 * the backend re-checks the role on every request.
 */
export interface AdminUser {
    id: string;
    email: string;
    role: AdminRole;
}

/**
 * Credentials an organizer submits. A domain-level input type so the layers above the
 * repository never see the wire DTO; the repository maps it at the boundary.
 */
export interface AdminCredentials {
    email: string;
    password: string;
}

/**
 * A registration request. Without an invite the backend creates a `PENDING` account
 * that an owner must approve; with a valid [inviteToken] the account is activated
 * immediately and the invite is consumed.
 */
export interface AdminRegistration extends AdminCredentials {
    inviteToken?: string;
}
