/**
 * The lead organizer of a tournament: the one who created it. Email only; the lead is never added.
 * A disabled lead cannot sign in, so the co-organizers keep running the tournament meanwhile.
 */
export interface LeadOrganizer {
    email: string;
    disabled: boolean;
}

/**
 * A co-organizer, with the instant the lead organizer added them. A disabled co-organizer stays
 * on the list; their access is paused until the account is reactivated.
 */
export interface CoOrganizer {
    adminUserId: string;
    email: string;
    addedAt: Date;
    disabled: boolean;
}

/**
 * The organizer list of one tournament as the lead organizer manages it: the lead first,
 * then the co-organizers in the order they were added.
 */
export interface Organizers {
    lead: LeadOrganizer;
    coOrganizers: CoOrganizer[];
}
