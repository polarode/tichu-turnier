package tichu.application.api

import tichu.domain.AdminUserId
import tichu.domain.Team
import tichu.domain.TeamId
import tichu.domain.TournamentId

interface GetTeam {
    /**
     * Fails as not-found when the tournament or the team does not exist, or when
     * either belongs to another admin.
     */
    fun get(tournamentId: TournamentId, teamId: TeamId, owner: AdminUserId): Team
}
