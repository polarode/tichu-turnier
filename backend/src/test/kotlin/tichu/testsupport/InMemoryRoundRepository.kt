package tichu.testsupport

import tichu.domain.Round
import tichu.domain.RoundId
import tichu.domain.RoundRepository
import tichu.domain.TableKey
import tichu.domain.TournamentId
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory implementation of the [RoundRepository] port, kept as a fast fake
 * for unit tests. The production adapter is [tichu.infra.persistence.JpaRoundRepository].
 */
class InMemoryRoundRepository : RoundRepository {

    private val store = ConcurrentHashMap<String, Round>()

    override fun save(round: Round): Round {
        store[round.id.value] = round
        return round
    }

    override fun findByTournamentAndNumber(tournamentId: TournamentId, number: Int): Round? =
        store.values
            .filter { it.tournamentId == tournamentId && it.number == number }
            .maxByOrNull { it.id.value }

    override fun findByTournament(tournamentId: TournamentId): List<Round> =
        store.values.filter { it.tournamentId == tournamentId }.sortedBy { it.number }

    /** Single-threaded unit tests need no serialization; the JPA adapter takes a row lock. */
    override fun lockTable(key: TableKey) = Unit
}
