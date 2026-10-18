package com.example.template.application.usecase

import com.example.template.application.api.GetTeam
import com.example.template.domain.AdminUserId
import com.example.template.domain.Team
import com.example.template.domain.TeamId
import com.example.template.domain.TeamRepository
import com.example.template.domain.TournamentId
import com.example.template.domain.TournamentRepository
import com.example.template.vocabulary.NotFoundException

/**
 * Fetches one team of the caller's tournament. Both scoping dimensions are checked:
 * the tournament must be owned by [owner], and the team must belong to that
 * tournament — a valid team id from a foreign tournament fails as not-found, never
 * as forbidden, so the caller cannot learn that the id exists.
 */
class GetTeamQuery(
    private val tournamentRepository: TournamentRepository,
    private val teamRepository: TeamRepository,
) : GetTeam {

    override fun get(tournamentId: TournamentId, teamId: TeamId, owner: AdminUserId): Team {
        tournamentRepository.findOwnedOrThrow(tournamentId, owner)
        return teamRepository.findById(teamId, tournamentId)
            ?: throw NotFoundException("Team ${teamId.value} not found in tournament ${tournamentId.value}")
    }
}
