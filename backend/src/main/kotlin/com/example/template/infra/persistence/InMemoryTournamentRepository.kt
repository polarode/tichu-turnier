package com.example.template.infra.persistence

import com.example.template.domain.AdminUserId
import com.example.template.domain.Tournament
import com.example.template.domain.TournamentId
import com.example.template.domain.TournamentRepository
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory implementation of the [TournamentRepository] port, kept as a fast fake
 * for unit tests (see `TournamentUseCasesTest`). The production adapter is
 * [JpaTournamentRepository] on PostgreSQL; swapping it never touches the domain or
 * use cases — they depend only on the port.
 */
class InMemoryTournamentRepository : TournamentRepository {

    private val store = ConcurrentHashMap<String, Tournament>()

    override fun save(tournament: Tournament): Tournament {
        store[tournament.id.value] = tournament
        return tournament
    }

    override fun findById(id: TournamentId): Tournament? = store[id.value]

    override fun findAllByOwner(owner: AdminUserId): List<Tournament> =
        store.values.filter { it.isOwnedBy(owner) }

    override fun delete(id: TournamentId) {
        store.remove(id.value)
    }
}
