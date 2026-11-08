package tichu.application.usecase

import tichu.application.api.GetTournament
import tichu.domain.AdminUserId
import tichu.domain.Tournament
import tichu.domain.TournamentId
import tichu.domain.TournamentRepository

class GetTournamentQuery(
    private val tournamentAccess: TournamentAccess,
) : GetTournament {
    override fun get(id: TournamentId, owner: AdminUserId): Tournament =
        tournamentAccess.organizedOrThrow(id, owner)
}
