package tichu.application.usecase

import tichu.application.api.TeamWriteRequest
import tichu.application.api.UpdateTeam
import tichu.domain.AdminUserId
import tichu.domain.Team
import tichu.domain.TeamId
import tichu.domain.TeamRepository
import tichu.domain.TournamentId
import tichu.domain.TournamentRepository
import tichu.vocabulary.NotFoundException
import org.springframework.transaction.annotation.Transactional

/**
 * Renames a team and/or swaps its two players. Ownership is checked on the tournament
 * first (a foreign tournament is a 404), then the team must belong to that tournament
 * (a foreign or unknown team id is a 404 as well, so the caller never learns that an
 * id exists elsewhere). Only then does the setup lock apply: outside `setup` the team
 * answers `409 TOURNAMENT_NOT_EDITABLE` before any field validation runs.
 *
 * The full field and uniqueness matrix from creation is re-applied through
 * [TeamInputValidator], with this team excluded from the uniqueness checks so keeping
 * its own name or players is not a self-clash. Player ids are preserved — the two
 * slots keep their identity while their names change.
 *
 * The save replaces the two player rows, so the update is one transaction across the
 * teams and players tables. A plain class wired as a bean in [UseCaseConfiguration].
 */
@Transactional
class UpdateTeamCommand(
    private val tournamentAccess: TournamentAccess,
    private val teamRepository: TeamRepository,
) : UpdateTeam {

    override fun update(
        tournamentId: TournamentId,
        teamId: TeamId,
        command: TeamWriteRequest,
        owner: AdminUserId,
    ): Team {
        val tournament = tournamentAccess.organizedOrThrow(tournamentId, owner)
        val team = teamRepository.findById(teamId, tournamentId)
            ?: throw NotFoundException("Team ${teamId.value} not found in tournament ${tournamentId.value}")
        tournament.requireSetupPhase()

        val name = command.name.trim()
        val playerOneName = command.playerOneName.trim()
        val playerTwoName = command.playerTwoName.trim()
        TeamInputValidator.validate(name, playerOneName, playerTwoName)
        TeamInputValidator.validateUniqueness(
            teams = teamRepository.findAllByTournament(tournamentId),
            name = name,
            playerOneName = playerOneName,
            playerTwoName = playerTwoName,
            excludingTeamId = teamId,
        )

        val updated = team.copy(
            name = name,
            players = team.players.mapIndexed { index, player ->
                player.copy(name = if (index == 0) playerOneName else playerTwoName)
            },
        )
        return teamRepository.save(updated)
    }
}
