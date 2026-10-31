package com.example.template.testsupport

import com.example.template.domain.Game
import com.example.template.domain.GameRepository
import com.example.template.domain.TournamentId
import com.example.template.vocabulary.GameAlreadyExistsException
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory implementation of the [GameRepository] port, kept as a fast fake
 * for unit tests. Mirrors the JPA adapter's semantics: natural-key uniqueness on
 * create, conditional version update (first commit wins), participants replaced
 * wholesale. The production adapter is
 * [com.example.template.infra.persistence.JpaGameRepository].
 */
class InMemoryGameRepository : GameRepository {

    /** Keyed by the natural key as one string. */
    private val store = ConcurrentHashMap<String, Game>()

    private fun key(
        tournamentId: TournamentId,
        roundNumber: Int,
        tableNumber: Int,
        gameNumber: Int,
    ): String = "${tournamentId.value}/$roundNumber/$tableNumber/$gameNumber"

    override fun create(game: Game): Game {
        val k = key(game.tournamentId, game.roundNumber, game.tableNumber, game.gameNumber)
        if (store.putIfAbsent(k, game.copy(id = nextId(), version = 1)) != null) {
            throw GameAlreadyExistsException(
                "Game ${game.gameNumber} at table ${game.tableNumber} of round ${game.roundNumber} already exists",
            )
        }
        return store[k]!!
    }

    override fun updateWithVersion(
        tournamentId: TournamentId,
        roundNumber: Int,
        tableNumber: Int,
        gameNumber: Int,
        expectedVersion: Int,
        update: (Game) -> Game,
    ): Game? {
        val k = key(tournamentId, roundNumber, tableNumber, gameNumber)
        store[k]?.let { current ->
            if (current.version != expectedVersion) return null
            val updated = update(current).copy(version = current.version + 1)
            store[k] = updated
            return updated
        }
        return null
    }

    override fun find(
        tournamentId: TournamentId,
        roundNumber: Int,
        tableNumber: Int,
        gameNumber: Int,
    ): Game? = store[key(tournamentId, roundNumber, tableNumber, gameNumber)]

    private fun nextId(): Long = idCounter++.let { if (it == 0L) 1L else it }

    private var idCounter = 1L
}
