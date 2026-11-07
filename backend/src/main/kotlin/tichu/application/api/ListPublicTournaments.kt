package tichu.application.api

import tichu.domain.TournamentId
import tichu.domain.TournamentStatus

interface ListPublicTournaments {
    /**
     * The public tournaments, ordered ACTIVE, COMPLETED, PUBLISHED and by name A–Z within
     * each status. Carries only public facts; never the owner or any credential.
     */
    fun list(): List<PublicTournament>
}

/** Public read model of a tournament for the public home. Deliberately has no owner. */
data class PublicTournament(
    val id: TournamentId,
    val name: String,
    val status: TournamentStatus,
    val teamCount: Int,
)
