package tichu.domain

import kotlin.random.Random

/** The pairings of all previous rounds, consulted to avoid rematches. */
data class PairingHistory(val pairings: List<Pair<TeamId, TeamId>>) {
    private val played: Set<Set<TeamId>> = pairings.map { setOf(it.first, it.second) }.toSet()

    fun hasRematch(a: TeamId, b: TeamId): Boolean = setOf(a, b) in played

    companion object {
        /** The history of the given previous-round [tables]; every pair of a trio table counts as having met. */
        fun of(tables: List<Table>): PairingHistory = PairingHistory(tables.flatMap { it.pairs })
    }
}

/** Identifier of a [Table]; a sequential 1-based number assigned in pairing order. */
@JvmInline
value class TableId(val value: Int)

/**
 * One table of a round: two seated teams, or three at a trio table (GLOSSARY.md),
 * in seat order A/B/C, with the table's own id. The id is explicit (not an
 * implicit list index) so configurable table names can be added later without
 * changing the shape of a pairing.
 *
 * "Who sits here" ([seats], [seatedTeams]) and "who plays game n"
 * ([playingTeams]) are kept apart: at a trio table one seated team sits out
 * each game.
 */
data class Table(
    val id: TableId,
    val seatedTeams: List<TeamId>,
) {
    init {
        require(seatedTeams.size in 2..TRIO_SIZE) { "A table seats 2 or 3 teams, got ${seatedTeams.size}" }
        require(seatedTeams.distinct().size == seatedTeams.size) { "The seated teams of a table must be distinct" }
    }

    /** Whether this is the round's trio table, seating three teams. */
    val isTrio: Boolean
        get() = seatedTeams.size == TRIO_SIZE

    /** Games played here per round: four at a normal table, six at a trio table. */
    val gameCount: Int
        get() = if (isTrio) TRIO_SCHEDULE.size else NORMAL_GAME_COUNT

    /** Whether [teamId] is seated at this table, whether or not it plays a given game. */
    fun seats(teamId: TeamId): Boolean = teamId in seatedTeams

    /**
     * The two teams playing game [gameNumber] (1-based), in seat order. Both teams
     * play every game at a normal table; a trio table follows the fixed schedule
     * 1 AB, 2 AC, 3 BC, 4 AB, 5 AC, 6 BC.
     */
    fun playingTeams(gameNumber: Int): List<TeamId> {
        require(gameNumber in 1..gameCount) { "Game number must be in 1..$gameCount, got $gameNumber" }
        if (!isTrio) return seatedTeams
        return TRIO_SCHEDULE[gameNumber - 1].map { seatedTeams[it] }
    }

    /** Who plays each game here, in game-number order. */
    val schedule: List<ScheduledGame>
        get() = (1..gameCount).map { ScheduledGame(it, playingTeams(it)) }

    /** Every pair of teams meeting at this table: one pair, or all three at a trio table. */
    val pairs: List<Pair<TeamId, TeamId>>
        get() = seatedTeams.flatMapIndexed { i, a -> seatedTeams.drop(i + 1).map { b -> Pair(a, b) } }

    companion object {
        /** Teams seated at a trio table. */
        const val TRIO_SIZE = 3

        private const val NORMAL_GAME_COUNT = 4

        /** Seat indexes playing each trio game: every pair twice, no team sits out twice in a row. */
        private val TRIO_SCHEDULE = listOf(listOf(0, 1), listOf(0, 2), listOf(1, 2), listOf(0, 1), listOf(0, 2), listOf(1, 2))
    }
}

/** One game of a [Table]'s schedule: its 1-based [gameNumber] and the two [playingTeams]. */
data class ScheduledGame(val gameNumber: Int, val playingTeams: List<TeamId>)

/** The pairing of a round: every participating team sits at exactly one [Table]. */
data class Pairing(val tables: List<Table>)

/**
 * Pure, framework-free pairing component: teams → [Pairing]. Deterministic given
 * the input and the injected [Random]; a seeded instance reproduces a pairing
 * exactly. No persistence, no Spring — unit-tested in isolation.
 */
object SwissPairing {

    /**
     * Round 1: a uniform random shuffle of the teams, paired adjacent and seated
     * at sequentially numbered tables (Table 1 = first shuffled pair). With an odd
     * count the last three shuffled teams form the trio table, seated last.
     */
    fun pairFirstRound(teamIds: List<TeamId>, random: Random): Pairing {
        val shuffled = teamIds.shuffled(random)
        if (shuffled.size % 2 == 0) return pairingOf(shuffled.chunked(2))
        val trio = shuffled.takeLast(Table.TRIO_SIZE)
        return pairingOf(shuffled.dropLast(Table.TRIO_SIZE).chunked(2) + listOf(trio))
    }

    /**
     * Rounds 2+: classic Swiss in the order of the current [standings] (the one
     * ordering, see [Standings.rank]). With an odd count the bottom three form the
     * trio table, seated last in standings order, regardless of history; teams
     * sharing the rank at that cut-off are drawn with [random]. The rest pair by a
     * search for a rematch-free assignment (backtracking); when none exists, it
     * picks the one with the fewest rematches. Among equally good pairings the one
     * closest to the standings order wins. Only the trio draw is random: even
     * counts never consume [random], so they depend on the input alone.
     */
    fun pairNextRound(standings: Standings, history: PairingHistory, random: Random): Pairing {
        val entries = standings.entries
        val trio = if (entries.size % 2 == 1) bottomThree(entries, random) else emptyList()
        val pairs = bestPairing(entries.map { it.team.teamId } - trio.toSet(), history)
        val seatings = pairs.map { it.toList() }
        return pairingOf(if (trio.isEmpty()) seatings else seatings + listOf(trio))
    }

    /**
     * The bottom three of [standings] (already ranked), in standings order. The tie
     * group straddling the cut-off (the third-from-bottom team's shared rank) fills
     * the trio's open seats by a draw with [random].
     */
    private fun bottomThree(standings: List<StandingsEntry>, random: Random): List<TeamId> {
        val cutOff = standings[standings.size - Table.TRIO_SIZE]
        val tied = standings.filter { it.rank == cutOff.rank }
        val below = standings.drop(standings.indexOf(tied.last()) + 1)
        val openSeats = Table.TRIO_SIZE - below.size
        val drawn = if (tied.size > openSeats) tied.shuffled(random).take(openSeats) else tied
        return standings.filter { it in drawn || it in below }.map { it.team.teamId }
    }

    /** Seats each seating, in seat order, at sequentially numbered tables: Table 1 = first seating. */
    private fun pairingOf(seatings: List<List<TeamId>>): Pairing = Pairing(
        tables = seatings.mapIndexed { index, seatedTeams -> Table(TableId(index + 1), seatedTeams) },
    )

    /**
     * Depth-first search over pairings of [teams] (already in standings order),
     * returning the pairing with the fewest rematches. Candidates are tried in
     * standings order, so the first minimal pairing found is the one closest to
     * the adjacent standings pairing. Branches that cannot beat the best known
     * rematch count are pruned; the search stays exact. Practical for realistic
     * tournament sizes (a few dozen teams).
     */
    private fun bestPairing(
        teams: List<TeamId>,
        history: PairingHistory,
    ): List<Pair<TeamId, TeamId>> {
        if (teams.isEmpty()) return emptyList()
        val first = teams.first()
        var best: List<Pair<TeamId, TeamId>>? = null
        var bestRematches = Int.MAX_VALUE
        for (candidate in teams.drop(1)) {
            val rematchPenalty = if (history.hasRematch(first, candidate)) 1 else 0
            if (rematchPenalty >= bestRematches) continue
            val rest = teams.drop(1).filter { it != candidate }
            val subPairing = bestPairing(rest, history)
            val totalRematches = rematchPenalty + rematchCount(subPairing, history)
            if (totalRematches < bestRematches) {
                bestRematches = totalRematches
                best = listOf(Pair(first, candidate)) + subPairing
            }
        }
        return best!!
    }

    private fun rematchCount(pairs: List<Pair<TeamId, TeamId>>, history: PairingHistory): Int =
        pairs.count { (a, b) -> history.hasRematch(a, b) }
}
