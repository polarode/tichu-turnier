package tichu.testsupport

import tichu.domain.TableConfirmationRepository
import tichu.domain.TableId
import tichu.domain.TableKey
import tichu.domain.TeamId
import tichu.domain.TournamentId
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory implementation of the [TableConfirmationRepository] port, kept as a
 * fast fake for unit tests. The production adapter is
 * [tichu.infra.persistence.JpaTableConfirmationRepository].
 */
class InMemoryTableConfirmationRepository : TableConfirmationRepository {

    private val store = ConcurrentHashMap<TableKey, MutableSet<TeamId>>()

    override fun findByTable(key: TableKey): Set<TeamId> = store[key].orEmpty().toSet()

    override fun findByRound(tournamentId: TournamentId, roundNumber: Int): Map<TableId, Set<TeamId>> =
        store.filterKeys { it.tournamentId == tournamentId && it.roundNumber == roundNumber }
            .filterValues { it.isNotEmpty() }
            .map { (key, teams) -> TableId(key.tableNumber) to teams.toSet() }
            .toMap()

    override fun add(key: TableKey, teamId: TeamId, confirmedAt: Instant) {
        store.getOrPut(key) { ConcurrentHashMap.newKeySet() }.add(teamId)
    }

    override fun remove(key: TableKey, teamId: TeamId) {
        store[key]?.remove(teamId)
    }
}
