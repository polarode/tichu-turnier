package tichu.application.api

import tichu.domain.AdminUserId
import tichu.domain.TournamentId
import tichu.domain.Team

interface ListTeams {
    /** Fails as not-found when the tournament does not exist or is owned by another admin. */
    fun list(tournamentId: TournamentId, owner: AdminUserId): List<Team>
}
