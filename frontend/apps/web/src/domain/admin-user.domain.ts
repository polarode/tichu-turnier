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
