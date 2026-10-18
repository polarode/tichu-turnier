package com.example.template.application.usecase

import com.example.template.application.api.DeleteTeam
import com.example.template.domain.AdminUserId
import com.example.template.domain.TeamId
import com.example.template.domain.TeamRepository
import com.example.template.domain.TournamentId
import com.example.template.domain.TournamentRepository
import com.example.template.vocabulary.NotFoundException
import org.springframework.transaction.annotation.Transactional

/**
 * Deletes a team and its two players. Ownership is checked on the tournament first,
 * then the team must belong to that tournament — a foreign or unknown team id answers
 * 404 for every verb, never 403. The read and the delete share one transaction, so a
 * failure cannot leave the team without its players (the players cascade in the
 * schema, but the scoped delete is the application-level guarantee). A plain class
 * wired as a bean in [UseCaseConfiguration].
 */
@Transactional
class DeleteTeamCommand(
    private val tournamentRepository: TournamentRepository,
    private val teamRepository: TeamRepository,
) : DeleteTeam {

    override fun delete(tournamentId: TournamentId, teamId: TeamId, owner: AdminUserId) {
        tournamentRepository.findOwnedOrThrow(tournamentId, owner)
        teamRepository.findById(teamId, tournamentId)
            ?: throw NotFoundException("Team ${teamId.value} not found in tournament ${tournamentId.value}")
        teamRepository.delete(teamId, tournamentId)
    }
}
