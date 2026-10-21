/**
 * A team session as issued by exchanging an access link (see glossary: "Team Session").
 * The session cookie itself is httpOnly and never enters JS — this is only the
 * metadata the exchange response returns, and the UI needs none of it beyond
 * knowing the exchange succeeded.
 */
export interface TeamSession {
    teamId: string;
    tournamentId: string;
    expiresAt: Date;
}
