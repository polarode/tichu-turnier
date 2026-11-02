package tichu.application.api

import tichu.domain.AdminUserId
import tichu.domain.Tournament
import tichu.domain.TournamentId

interface GetTournament {
    /** Fails as not-found when [id] does not exist or is owned by another admin. */
    fun get(id: TournamentId, owner: AdminUserId): Tournament
}
