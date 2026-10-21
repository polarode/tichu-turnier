/**
 * The team's identity view as served by `GET /api/team/me` (see glossary: "Team
 * Session"). The session cookie scopes every field — there is no request parameter
 * a team session could abuse to read another team's data.
 */
export interface TeamOverview {
    teamId: string;
    tournamentId: string;
    tournamentName: string;
    teamName: string;
    players: { id: string; name: string }[];
}
