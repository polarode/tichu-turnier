package com.example.template.domain

import com.example.template.vocabulary.PairingUnsupportedException
import kotlin.random.Random

/**
 * One team's standing going into the next round: [victoryPoints] (primary rank)
 * and [tichuPoints] (tie-breaker), see GLOSSARY.md.
 */
data class PairingEntry(
    val teamId: TeamId,
    val victoryPoints: Int,
    val tichuPoints: Int,
)

/** The pairings of all previous rounds, consulted to avoid rematches. */
data class PairingHistory(val pairings: List<Pair<TeamId, TeamId>>) {
    private val played: Set<Set<TeamId>> = pairings.map { setOf(it.first, it.second) }.toSet()

    fun hasRematch(a: TeamId, b: TeamId): Boolean = setOf(a, b) in played
}

/** Identifier of a [Table]; a sequential 1-based number assigned in pairing order. */
@JvmInline
value class TableId(val value: Int)

/**
 * One table of a round: two teams meeting, with the table's own id. The id is
 * explicit (not an implicit list index) so configurable table names can be added
 * later without changing the shape of a pairing.
 */
data class Table(
    val id: TableId,
    val home: TeamId,
    val away: TeamId,
)

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
     * at sequentially numbered tables (Table 1 = first shuffled pair).
     */
    fun pairFirstRound(teamIds: List<TeamId>, random: Random): Pairing {
        requireEvenTeamCount(teamIds.size)
        val shuffled = teamIds.shuffled(random)
        return pairingOf(shuffled.chunked(2).map { (home, away) -> Pair(home, away) })
    }

    private fun requireEvenTeamCount(count: Int) {
        if (count % 2 == 1) {
            throw PairingUnsupportedException(
                "Swiss pairing needs an even number of teams, got $count. " +
                    "Trio tables are not supported yet.",
            )
        }
    }

    /**
     * Rounds 2+: classic Swiss — standings sorted by VP desc, TP desc, team id asc.
     * Pairing searches for a rematch-free assignment (backtracking); when none exists,
     * it picks the one with the fewest rematches. Among equally good pairings the one
     * closest to the standings order wins, so the result is fully deterministic given
     * the input alone — no randomness involved in a Swiss round.
     */
    fun pairNextRound(entries: List<PairingEntry>, history: PairingHistory): Pairing {
        requireEvenTeamCount(entries.size)
        val standings = entries.sortedWith(
            compareByDescending<PairingEntry> { it.victoryPoints }
                .thenByDescending { it.tichuPoints }
                .thenBy { it.teamId.value },
        )
        val pairs = bestPairing(standings.map { it.teamId }, history)
        return pairingOf(pairs)
    }

    /** Seats the pairs at sequentially numbered tables: Table 1 = first pair. */
    private fun pairingOf(pairs: List<Pair<TeamId, TeamId>>): Pairing = Pairing(
        tables = pairs.mapIndexed { index, (home, away) ->
            Table(TableId(index + 1), home, away)
        },
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
