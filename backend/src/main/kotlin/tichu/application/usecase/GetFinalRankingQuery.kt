package tichu.application.usecase

import tichu.application.api.AuthenticatedTeam
import tichu.application.api.GetFinalRanking
import tichu.domain.Standings
import tichu.domain.TeamRepository
import tichu.domain.TournamentRepository
import tichu.domain.TournamentStatus
import tichu.vocabulary.NotFoundException
import tichu.vocabulary.ResultsNotPublishedException

/**
 * The team's read of the Final Ranking (S7c, #116; GLOSSARY.md). Scoped to the
 * principal's own tournament and only readable once the organizer has published
 * the results: before publication no team learns the ranking, whatever the
 * tournament's status (setup, active or completed). Once published it is the same
 * [Standings.compute] read the admin sees.
 */
class GetFinalRankingQuery(
    private val tournamentRepository: TournamentRepository,
    private val teamRepository: TeamRepository,
    private val playedRounds: PlayedRoundsReader,
) : GetFinalRanking {
    override fun get(principal: AuthenticatedTeam): Standings {
        val tournamentId = principal.tournamentId
        val tournament = tournamentRepository.findById(tournamentId)
            ?: throw NotFoundException("Tournament ${tournamentId.value} not found")
        if (tournament.status != TournamentStatus.PUBLISHED) {
            throw ResultsNotPublishedException("Tournament ${tournamentId.value} has no published results yet")
        }
        val rounds = playedRounds.of(tournamentId)
        return Standings.compute(teamRepository.findAllByTournament(tournamentId), rounds, tournament.settings)
    }
}
