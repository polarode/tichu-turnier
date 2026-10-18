package com.example.template.domain

/**
 * A team of a [Tournament]: a unique team name plus exactly two distinct
 * players. The players are tournament-scoped, so the same player may play in
 * several tournaments but only on one team per tournament.
 */
data class Team(
    val id: TeamId,
    val tournamentId: TournamentId,
    val name: String,
    val players: List<Player>,
) {
    init {
        require(players.size == 2) {
            "A team must have exactly two players, got ${players.size}"
        }
        require(players.distinctBy { it.id }.size == 2) {
            "The two players of a team must be distinct"
        }
    }
}

/** Identifier of a [Team], scoped to its [TournamentId]. */
@JvmInline
value class TeamId(val value: String)
