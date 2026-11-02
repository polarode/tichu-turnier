package tichu.application.api

import tichu.domain.AdminUserId
import tichu.domain.TeamId
import tichu.domain.TournamentId

/** Inbound port (driving side). Controllers depend on this, never on the implementation. */
interface DeleteTeam {
    /**
     * Removes the team and its players from the tournament owned by [owner]. Fails as
     * not-found when the tournament or the team does not exist, or when either belongs
     * to another admin.
     */
    fun delete(tournamentId: TournamentId, teamId: TeamId, owner: AdminUserId)
}
