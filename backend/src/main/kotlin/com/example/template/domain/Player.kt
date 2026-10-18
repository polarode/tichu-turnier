package com.example.template.domain

/**
 * A named participant of a [Team]. Players are tournament-scoped — there is no
 * global player table — so the same player name may exist in different
 * tournaments, but a given tournament knows each player exactly once (a player
 * belongs to exactly one team of that tournament).
 */
data class Player(
    val id: PlayerId,
    val tournamentId: TournamentId,
    val teamId: TeamId,
    val name: String,
)

/** Identifier of a [Player], scoped to its [TournamentId]. */
@JvmInline
value class PlayerId(val value: String)
