package tichu.domain

/**
 * A named participant of a [Team]. Players are tournament-scoped — there is no
 * global player table — and each belongs to exactly one team. Names are not
 * identities: the same name may appear on several teams of one tournament (the
 * team tells them apart), only the two players of one team must differ.
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
