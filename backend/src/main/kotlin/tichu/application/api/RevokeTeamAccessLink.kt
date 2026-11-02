package tichu.application.api

import tichu.domain.AdminUserId
import tichu.domain.TeamId
import tichu.domain.TournamentId

/**
 * Inbound port (driving side). Owner-only: disables the team's access link —
 * further exchanges fail with the generic rejection, and the team's live sessions
 * end on their next request. Idempotent; only rotation revives the link.
 */
interface RevokeTeamAccessLink {
    fun revoke(tournamentId: TournamentId, teamId: TeamId, owner: AdminUserId)
}