/**
 * The authenticated organizer as the UI knows them. Deliberately minimal: the wire
 * response never carries password or token material, so neither does the domain model.
 */
export interface AdminUser {
    id: string;
    email: string;
}
