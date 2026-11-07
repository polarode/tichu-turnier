package tichu.application.api

import tichu.domain.PlayerId
import tichu.domain.TeamId
import tichu.domain.TournamentId
import tichu.domain.TournamentStatus

interface GetPublicTournament {
    /**
     * The public pairings of one tournament: every drawn round, its tables in table order
     * and each table's teams in seat order with their players. Only public facts; never
     * the owner, a credential, a game, a result or a table status.
     *
     * Throws `NotFoundException` unless the tournament is public and in a publicly listable
     * status (ACTIVE, COMPLETED or PUBLISHED). Setup, non-public and unknown ids look alike.
     */
    fun get(id: TournamentId): PublicTournamentDetail
}

/** Public read model of a tournament's pairings. Deliberately has no owner. */
data class PublicTournamentDetail(
    val id: TournamentId,
    val name: String,
    val status: TournamentStatus,
    /** Ordered by round number, ascending. */
    val rounds: List<PublicRound>,
)

/** One drawn round of a public tournament. */
data class PublicRound(
    val number: Int,
    /** Ordered by table number. */
    val tables: List<PublicTable>,
)

/** One table of a public round; [tableNumber] is the domain's 1-based number, the letter is UI-only. */
data class PublicTable(
    val tableNumber: Int,
    /** Whether this is the round's trio table, seating three teams. */
    val trio: Boolean,
    /** In seat order. */
    val teams: List<PublicTeam>,
)

/** A team seated at a public table, with its players. */
data class PublicTeam(
    val id: TeamId,
    val name: String,
    val players: List<PublicPlayer>,
)

/** A player of a public team. */
data class PublicPlayer(
    val id: PlayerId,
    val name: String,
)
