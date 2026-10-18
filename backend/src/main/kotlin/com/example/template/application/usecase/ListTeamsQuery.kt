package com.example.template.application.usecase

import com.example.template.application.api.ListTeams
import com.example.template.domain.AdminUserId
import com.example.template.domain.Team
import com.example.template.domain.TeamRepository
import com.example.template.domain.TournamentId
import com.example.template.domain.TournamentRepository

/** Lists the teams of the caller's tournament. Ownership-scoped: 404 on foreign ids. */
class ListTeamsQuery(
    private val tournamentRepository: TournamentRepository,
    private val teamRepository: TeamRepository,
) : ListTeams {

    override fun list(tournamentId: TournamentId, owner: AdminUserId): List<Team> {
        tournamentRepository.findOwnedOrThrow(tournamentId, owner)
        return teamRepository.findAllByTournament(tournamentId)
    }
}
