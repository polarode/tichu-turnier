package com.example.template.testsupport

import com.example.template.domain.Round
import com.example.template.domain.RoundId
import com.example.template.domain.RoundRepository
import com.example.template.domain.TournamentId
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory implementation of the [RoundRepository] port, kept as a fast fake
 * for unit tests. The production adapter is [com.example.template.infra.persistence.JpaRoundRepository].
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
}
