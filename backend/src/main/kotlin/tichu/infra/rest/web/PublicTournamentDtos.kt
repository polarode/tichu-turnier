package tichu.infra.rest.web

import tichu.application.api.PublicTournament
import tichu.domain.TournamentStatus

/**
 * Wire representation of one public tournament. Public facts only (ADR 0008): no owner,
 * no tokens or credentials, no access-link state.
 */
data class PublicTournamentResponse(
    val id: String,
    val name: String,
    val status: TournamentStatus,
    val teamCount: Int,
) {
    companion object {
        fun from(tournament: PublicTournament): PublicTournamentResponse = PublicTournamentResponse(
            id = tournament.id.value,
            name = tournament.name,
            status = tournament.status,
            teamCount = tournament.teamCount,
        )
    }
}
