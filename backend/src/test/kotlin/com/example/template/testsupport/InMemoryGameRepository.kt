package com.example.template.testsupport

import com.example.template.domain.Game
import com.example.template.domain.GameKey
import com.example.template.domain.GameRepository
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

    private fun keyOf(key: GameKey): String =
        "${key.tournamentId.value}/${key.roundNumber}/${key.tableNumber}/${key.gameNumber}"

    override fun create(game: Game): Game {
        val k = keyOf(game.key)
        if (store.putIfAbsent(k, game.copy(id = nextId(), version = 1)) != null) {
            throw GameAlreadyExistsException(
                "Game ${game.gameNumber} at table ${game.tableNumber} of round ${game.roundNumber} already exists",
            )
        }
        return store[k]!!
    }

    override fun updateWithVersion(key: GameKey, expectedVersion: Int, update: (Game) -> Game): Game? {
        val k = keyOf(key)
        store[k]?.let { current ->
            if (current.version != expectedVersion) return null
            val updated = update(current).copy(version = current.version + 1)
            store[k] = updated
            return updated
        }
        return null
    }

    override fun find(key: GameKey): Game? = store[keyOf(key)]

    private fun nextId(): Long = idCounter++.let { if (it == 0L) 1L else it }

    private var idCounter = 1L
}
