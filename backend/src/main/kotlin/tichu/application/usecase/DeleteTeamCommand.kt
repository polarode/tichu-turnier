package tichu.application.usecase

import tichu.application.api.DeleteTeam
import tichu.domain.AdminUserId
import tichu.domain.TeamId
import tichu.domain.TeamRepository
import tichu.domain.TeamTokenRepository
import tichu.domain.TournamentId
import tichu.domain.TournamentRepository
import tichu.domain.TournamentStatus
import tichu.vocabulary.NotFoundException
import tichu.vocabulary.TournamentNotEditableException
import org.springframework.transaction.annotation.Transactional

/**
 * Deletes a team, its two players and its access token. Ownership is checked on the
 * tournament first,
 * then the team must belong to that tournament — a foreign or unknown team id answers
 * 404 for every verb, never 403. The read and the deletes share one transaction, so a
 * failure cannot leave a team half-removed (the players and the token also cascade in
 * the schema, but the scoped deletes are the application-level guarantee). Outside
 * `setup` the found team answers `409 TOURNAMENT_NOT_EDITABLE` before anything is deleted.
 * A plain class wired as a bean in [UseCaseConfiguration].
 */
@Transactional
class DeleteTeamCommand(
    private val tournamentRepository: TournamentRepository,
    private val teamRepository: TeamRepository,
    private val teamTokenRepository: TeamTokenRepository,
) : DeleteTeam {

    override fun delete(tournamentId: TournamentId, teamId: TeamId, owner: AdminUserId) {
        val tournament = tournamentRepository.findOwnedOrThrow(tournamentId, owner)
        teamRepository.findById(teamId, tournamentId)
            ?: throw NotFoundException("Team ${teamId.value} not found in tournament ${tournamentId.value}")
        if (tournament.status != TournamentStatus.SETUP) {
            throw TournamentNotEditableException(
                "Tournament ${tournamentId.value} can only be edited while in setup " +
                    "(current status: ${tournament.status})",
            )
        }
        teamRepository.delete(teamId, tournamentId)
        teamTokenRepository.deleteByTeamId(teamId)
    }
}
