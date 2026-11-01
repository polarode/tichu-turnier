import type { TournamentStatus } from '@/vocabulary/tournament-status.enum.ts';

/**
 * The team's identity view as served by `GET /api/team/me` (see glossary: "Team
 * Session"). The session cookie scopes every field — there is no request parameter
 * a team session could abuse to read another team's data. The tournament status
 * lets the team space gate state-dependent controls (scoring, schedule).
 */
export interface TeamOverview {
    teamId: string;
    tournamentId: string;
    tournamentName: string;
    tournamentStatus: TournamentStatus;
    teamName: string;
    players: { id: string; name: string }[];
}
