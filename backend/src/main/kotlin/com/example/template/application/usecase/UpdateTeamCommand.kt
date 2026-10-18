package com.example.template.application.usecase

import com.example.template.application.api.TeamWriteRequest
import com.example.template.application.api.UpdateTeam
import com.example.template.domain.AdminUserId
import com.example.template.domain.Team
import com.example.template.domain.TeamId
import com.example.template.domain.TeamRepository
import com.example.template.domain.TournamentId
import com.example.template.domain.TournamentRepository
import com.example.template.vocabulary.NotFoundException
import org.springframework.transaction.annotation.Transactional

/**
 * Renames a team and/or swaps its two players. Ownership is checked on the tournament
 * first (a foreign tournament is a 404), then the team must belong to that tournament
 * (a foreign or unknown team id is a 404 as well, so the caller never learns that an
 * id exists elsewhere).
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
    private val tournamentRepository: TournamentRepository,
    private val teamRepository: TeamRepository,
) : UpdateTeam {

    override fun update(
        tournamentId: TournamentId,
        teamId: TeamId,
        command: TeamWriteRequest,
        owner: AdminUserId,
    ): Team {
        tournamentRepository.findOwnedOrThrow(tournamentId, owner)
        val team = teamRepository.findById(teamId, tournamentId)
            ?: throw NotFoundException("Team ${teamId.value} not found in tournament ${tournamentId.value}")

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
