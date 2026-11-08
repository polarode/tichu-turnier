/** The lead organizer of a tournament: the one who created it. Email only; the lead is never added. */
export interface LeadOrganizer {
    email: string;
}

/** A co-organizer, with the instant the lead organizer added them. */
export interface CoOrganizer {
    email: string;
    addedAt: Date;
}

/**
 * The organizer list of one tournament as the lead organizer manages it: the lead first,
 * then the co-organizers in the order they were added.
 */
export interface Organizers {
    lead: LeadOrganizer;
    coOrganizers: CoOrganizer[];
}
