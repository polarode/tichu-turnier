package com.example.template.application.usecase

import com.example.template.application.api.AuthenticatedTeam
import com.example.template.application.api.GetOwnTeam
import com.example.template.domain.Team
import com.example.template.domain.TeamRepository
import com.example.template.vocabulary.NotFoundException

/**
 * The use-case-level guard for team sessions: it resolves the principal's own
 * team and nothing else. Both scoping dimensions come from the authenticated
 * team session — a team session cannot name a foreign team id, because the
 * query never receives one.
 */
class GetOwnTeamQuery(
    private val teamRepository: TeamRepository,
) : GetOwnTeam {

    override fun get(principal: AuthenticatedTeam): Team =
        teamRepository.findById(principal.teamId, principal.tournamentId)
            ?: throw NotFoundException("Team ${principal.teamId.value} not found")
}