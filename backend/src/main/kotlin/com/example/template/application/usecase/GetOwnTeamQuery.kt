package com.example.template.application.usecase

import com.example.template.application.api.AuthenticatedTeam
import com.example.template.application.api.GetOwnTeam
import com.example.template.application.api.TeamOverview
import com.example.template.domain.TeamRepository
import com.example.template.domain.TournamentRepository
import com.example.template.vocabulary.NotFoundException

/**
 * The use-case-level guard for team sessions: it resolves the principal's own
 * team (plus its tournament's display name) and nothing else. Both scoping
 * dimensions come from the authenticated team session — a team session cannot
 * name a foreign team id, because the query never receives one.
 */
class GetOwnTeamQuery(
    private val teamRepository: TeamRepository,
    private val tournamentRepository: TournamentRepository,
) : GetOwnTeam {

    override fun get(principal: AuthenticatedTeam): TeamOverview {
        val team = teamRepository.findById(principal.teamId, principal.tournamentId)
            ?: throw NotFoundException("Team ${principal.teamId.value} not found")
        val tournamentName = tournamentRepository.findById(team.tournamentId)?.name
            ?: throw NotFoundException("Tournament ${team.tournamentId.value} not found")
        return TeamOverview(team = team, tournamentName = tournamentName)
    }
}
