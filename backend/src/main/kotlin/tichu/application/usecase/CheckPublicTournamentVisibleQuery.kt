package tichu.application.usecase

import tichu.application.api.CheckPublicTournamentVisible
import tichu.domain.TournamentId
import tichu.domain.TournamentRepository
import tichu.vocabulary.NotFoundException

/** Visibility gate for public endpoints: one lookup, no pairings or results are built. */
class CheckPublicTournamentVisibleQuery(
    private val tournamentRepository: TournamentRepository,
) : CheckPublicTournamentVisible {

    override fun check(id: TournamentId) {
        val tournament = tournamentRepository.findById(id)
        if (tournament == null || !tournament.isVisibleToSpectators()) {
            throw NotFoundException("Tournament ${id.value} not found")
        }
    }
}
