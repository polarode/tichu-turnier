/**
 * The authenticated organizer as the UI knows them. Deliberately minimal: the wire
 * response never carries password or token material, so neither does the domain model.
 */
export interface AdminUser {
    id: string;
    email: string;
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
