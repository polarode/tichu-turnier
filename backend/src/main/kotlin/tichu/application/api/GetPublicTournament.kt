package tichu.application.api

import tichu.domain.PlayerId
import tichu.domain.TeamId
import tichu.domain.TournamentId
import tichu.domain.TournamentStatus

interface GetPublicTournament {
    /**
     * The public pairings of one tournament: every drawn round, its tables in table order
     * and each table's teams in seat order with their players. Only public facts; never
     * the owner, a credential, a game, an outcome, a table status or a confirmation.
     *
     * Once the tournament is PUBLISHED (and only then) it also carries each team's
     * [PublicTeam.totals] per table and the [PublicTournamentDetail.finalRanking]. Before
     * that both are null, so the payload stays the pairings alone.
     *
     * Throws `NotFoundException` unless the tournament is public and in a publicly listable
     * status (ACTIVE, COMPLETED or PUBLISHED). Setup, non-public and unknown ids look alike.
     */
    fun get(id: TournamentId): PublicTournamentDetail
}

/** Public read model of a tournament's pairings and, once published, its results. Deliberately has no owner. */
data class PublicTournamentDetail(
    val id: TournamentId,
    val name: String,
    val status: TournamentStatus,
    /** Ordered by round number, ascending. */
    val rounds: List<PublicRound>,
    /**
     * The Final Ranking in Standings order, shared ranks listed by name. Null unless the
     * tournament is PUBLISHED.
     */
    val finalRanking: List<PublicFinalRankingEntry>? = null,
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

/**
 * A team seated at a public table, with its players. [totals] is its Table Totals at this
 * table: null unless the tournament is PUBLISHED and the table is completed.
 */
data class PublicTeam(
    val id: TeamId,
    val name: String,
    val players: List<PublicPlayer>,
    val totals: PublicTableTotal? = null,
)

/** A player of a public team. */
data class PublicPlayer(
    val id: PlayerId,
    val name: String,
)

/** A team's summed Victory Points and Tichu Points at one table of one round (GLOSSARY.md, Table Totals). */
data class PublicTableTotal(
    val victoryPoints: Int,
    val tichuPoints: Int,
)

/** One line of the public Final Ranking: the team's summed results and the [rank] they earn. */
data class PublicFinalRankingEntry(
    val rank: Int,
    val teamId: TeamId,
    val teamName: String,
    val gamesPlayed: Int,
    val victoryPoints: Int,
    val tichuPoints: Int,
)
