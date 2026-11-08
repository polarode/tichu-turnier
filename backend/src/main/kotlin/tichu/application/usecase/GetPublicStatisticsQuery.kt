package tichu.application.usecase

import tichu.application.api.GetPublicStatistics
import tichu.application.api.TournamentStatistics
import tichu.domain.TeamRepository
import tichu.domain.TournamentId
import tichu.domain.TournamentRepository
import tichu.domain.TournamentStatus
import tichu.vocabulary.NotFoundException
import tichu.vocabulary.ResultsNotPublishedException

/**
 * The spectator's read of the tournament statistics (S12, #121). A tournament that is not
 * public, not visible to spectators, or unknown fails with the same not-found as the public
 * tournament page. A visible tournament shows its statistics only once PUBLISHED, which is
 * also where teams find them. Once published it is the shared [buildTournamentStatistics] read.
 */
class GetPublicStatisticsQuery(
    private val tournamentRepository: TournamentRepository,
    private val teamRepository: TeamRepository,
    private val playedRounds: PlayedRoundsReader,
) : GetPublicStatistics {
    override fun get(id: TournamentId): TournamentStatistics {
        val tournament = tournamentRepository.findById(id)
        if (tournament == null || !tournament.isVisibleToSpectators()) {
            throw NotFoundException("Tournament ${id.value} not found")
        }
        if (tournament.status != TournamentStatus.PUBLISHED) {
            throw ResultsNotPublishedException("Tournament ${id.value} has no published results yet")
        }
        return buildTournamentStatistics(
            tournament,
            teamRepository.findAllByTournament(id),
            playedRounds.of(id),
        )
    }
}
