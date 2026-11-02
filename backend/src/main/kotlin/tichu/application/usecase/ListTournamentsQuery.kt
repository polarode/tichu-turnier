package tichu.application.usecase

import tichu.application.api.ListTournaments
import tichu.domain.AdminUserId
import tichu.domain.Tournament
import tichu.domain.TournamentRepository

class ListTournamentsQuery(
    private val repository: TournamentRepository,
) : ListTournaments {
    override fun list(owner: AdminUserId): List<Tournament> =
        repository.findAllByOwner(owner).sortedByDescending { it.createdAt }
}
