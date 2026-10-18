package com.example.template.testsupport

import com.example.template.domain.Team
import com.example.template.domain.TeamId
import com.example.template.domain.TeamRepository
import com.example.template.domain.TournamentId
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory implementation of the [TeamRepository] port, kept as a fast fake
 * for unit tests (see `TeamUseCasesTest`). The production adapter is
 * [JpaTeamRepository]. Stores the teams with their players as one aggregate.
 */
class InMemoryTeamRepository : TeamRepository {

    private val store = ConcurrentHashMap<String, Team>()

    override fun save(team: Team): Team {
        store[team.id.value] = team
        return team
    }

    override fun findAllByTournament(tournamentId: TournamentId): List<Team> =
        store.values.filter { it.tournamentId == tournamentId }

    override fun findById(teamId: TeamId, tournamentId: TournamentId): Team? =
        store[teamId.value]?.takeIf { it.tournamentId == tournamentId }

    override fun delete(teamId: TeamId, tournamentId: TournamentId) {
        store.entries.removeIf { it.value.id == teamId && it.value.tournamentId == tournamentId }
    }
}
