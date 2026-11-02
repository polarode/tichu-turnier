package tichu.application.usecase

import tichu.application.api.GetTournament
import tichu.domain.AdminUserId
import tichu.domain.Tournament
import tichu.domain.TournamentId
import tichu.domain.TournamentRepository

class GetTournamentQuery(
    private val repository: TournamentRepository,
) : GetTournament {
    override fun get(id: TournamentId, owner: AdminUserId): Tournament =
        repository.findOwnedOrThrow(id, owner)
}
