package com.example.template.domain

/**
 * Outbound port (driven side of the hexagon). The domain declares what it needs;
 * an adapter in `infra/persistence` provides the implementation.
 *
 * Games are addressed by their [GameKey] — the surrogate id is internal.
 * Creation races are resolved by the natural-key unique constraint: [create]
 * fails when the game already exists.
 */
interface GameRepository {

    /**
     * Persists a new game with its participants (one aggregate, one transaction).
     * Returns the stored game with its surrogate id and version 1; throws
     * [GameAlreadyExistsException] when the natural key is taken.
     */
    fun create(game: Game): Game

    /**
     * Conditional update: persists the new facts only when the stored row still
     * carries [expectedVersion]. Returns the updated game (version bumped), or
     * null when the version did not match — the caller maps that to a conflict.
     * The whole update commits atomically with the participants.
     */
    fun updateWithVersion(key: GameKey, expectedVersion: Int, update: (Game) -> Game): Game?

    /** Exact lookup by natural key; null when the game was never created. */
    fun find(key: GameKey): Game?
}
