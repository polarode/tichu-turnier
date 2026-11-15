package tichu.testsupport

import tichu.domain.Team
import tichu.domain.TeamId
import tichu.domain.TeamRepository
import tichu.domain.TournamentId
import tichu.domain.Withdrawal
import tichu.vocabulary.NotFoundException
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

    override fun countByTournament(tournamentId: TournamentId): Int =
        store.values.count { it.tournamentId == tournamentId }

    override fun countByTournaments(ids: List<TournamentId>): Map<TournamentId, Int> =
        store.values
            .filter { it.tournamentId in ids }
            .groupingBy { it.tournamentId }
            .eachCount()

    override fun findById(teamId: TeamId, tournamentId: TournamentId): Team? =
        store[teamId.value]?.takeIf { it.tournamentId == tournamentId }

    override fun findById(teamId: TeamId): Team? = store[teamId.value]

    override fun delete(teamId: TeamId, tournamentId: TournamentId) {
        store.entries.removeIf { it.value.id == teamId && it.value.tournamentId == tournamentId }
    }

    override fun updateWithdrawal(teamId: TeamId, tournamentId: TournamentId, withdrawal: Withdrawal?) {
        val team = findById(teamId, tournamentId)
            ?: throw NotFoundException("Team ${teamId.value} not found in tournament ${tournamentId.value}")
        store[teamId.value] = team.copy(withdrawal = withdrawal)
    }
}
