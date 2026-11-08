package tichu.application.api

import tichu.domain.AdminUserId
import tichu.domain.TournamentId

interface GetTournament {
    /**
     * Fails as not-found when [id] does not exist or the caller does not organize it.
     * A co-organizer gets the tournament with [OrganizerRole.CO].
     */
    fun get(id: TournamentId, caller: AdminUserId): OrganizedTournament
}
