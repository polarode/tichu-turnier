package com.example.template.testsupport

import com.example.template.domain.TableConfirmationRepository
import com.example.template.domain.TableId
import com.example.template.domain.TableKey
import com.example.template.domain.TeamId
import com.example.template.domain.TournamentId
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory implementation of the [TableConfirmationRepository] port, kept as a
 * fast fake for unit tests. The production adapter is
 * [com.example.template.infra.persistence.JpaTableConfirmationRepository].
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
