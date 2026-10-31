package com.example.template.domain

/**
 * A game's natural key: the one tuple that identifies a played game. The API
 * addresses games by it — the surrogate id stays internal. Table numbers are
 * stable across the pairing's wholesale re-saves, so they anchor the key, not
 * a round table's surrogate id (see the V13 migration).
 */
data class GameKey(
    val tournamentId: TournamentId,
    val roundNumber: Int,
    val tableNumber: Int,
    val gameNumber: Int,
)
