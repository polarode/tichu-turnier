package tichu.application.api

import tichu.domain.AdminUserId
import tichu.domain.TeamId
import tichu.domain.TournamentId

/**
 * Inbound port (driving side). Owner-only: replaces the team's access token with
 * fresh material, resets the reveal flag (the new link is shown once via
 * [RevealTeamAccessLink]) and ends the team's live sessions on their next request.
 * Rotation also revives a revoked link — it is the documented recovery for a lost
 * or revoked link. Never returns token material.
 */
interface RotateTeamAccessLink {
    fun rotate(tournamentId: TournamentId, teamId: TeamId, owner: AdminUserId)
}