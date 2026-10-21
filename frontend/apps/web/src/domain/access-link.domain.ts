/**
 * A team's access link as shown to the organizer exactly once (see glossary:
 * "Access Link"). The raw token exists only in the reveal response — no listing,
 * detail view or later call can fetch it again; rotation is the documented recovery.
 */
export interface RevealedAccessLink {
    token: string;
    link: string;
}
