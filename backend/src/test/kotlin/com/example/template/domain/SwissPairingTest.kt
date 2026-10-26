package com.example.template.domain

import com.example.template.vocabulary.PairingUnsupportedException
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Unit tests for the pure Swiss pairing component. Seeded [Random] instances make
 * every operation reproducible: same input + same seed = same pairing.
 */
class SwissPairingTest {

    private fun teamIds(vararg names: String): List<TeamId> = names.map { TeamId(it) }

    @Test
    fun `first round pairs every team exactly once with sequential table ids`() {
        val teams = teamIds("a", "b", "c", "d", "e", "f")

        val pairing = SwissPairing.pairFirstRound(teams, Random(1))

        val paired = pairing.tables.flatMap { listOf(it.home, it.away) }
        assertEquals(teams.size, paired.size)
        assertEquals(teams.toSet(), paired.toSet())
        assertEquals(3, pairing.tables.size)
        assertEquals(listOf(1, 2, 3), pairing.tables.map { it.id.value })
    }

    @Test
    fun `first round is deterministic given the same seed`() {
        val teams = teamIds("a", "b", "c", "d", "e", "f")

        val pairing = SwissPairing.pairFirstRound(teams, Random(42))
        val again = SwissPairing.pairFirstRound(teams, Random(42))

        assertEquals(pairing, again)
    }

    @Test
    fun `first round shuffle actually varies across seeds`() {
        // Variance proxy: stdlib shuffled(random) is uniform by construction; here we
        // only pin that the shuffle genuinely varies instead of being constant.
        val teams = teamIds("a", "b", "c", "d", "e", "f")

        val pairings = (0 until 20).map { seed -> SwissPairing.pairFirstRound(teams, Random(seed)) }

        assertTrue(pairings.distinct().size > 1, "20 seeds produced only identical pairings")
    }

    @Test
    fun `odd team count is rejected in the first round`() {
        val teams = teamIds("a", "b", "c", "d", "e")

        val exception = assertFailsWith<PairingUnsupportedException> {
            SwissPairing.pairFirstRound(teams, Random(1))
        }

        assertTrue(exception.message!!.contains("even"))
    }

    @Test
    fun `next round pairs teams sorted by victory points, then tichu points, then id`() {
        val standings = listOf(
            PairingEntry(TeamId("d"), victoryPoints = 1, tichuPoints = 99),
            PairingEntry(TeamId("c"), victoryPoints = 2, tichuPoints = 0),
            PairingEntry(TeamId("b"), victoryPoints = 3, tichuPoints = 0),
            PairingEntry(TeamId("a"), victoryPoints = 4, tichuPoints = 0),
        )

        val pairing = SwissPairing.pairNextRound(standings, PairingHistory(emptyList()))

        assertEquals(
            listOf(
                Pair(TeamId("a"), TeamId("b")),
                Pair(TeamId("c"), TeamId("d")),
            ),
            pairing.tables.map { Pair(it.home, it.away) },
        )
        assertEquals(listOf(1, 2), pairing.tables.map { it.id.value })
    }

    @Test
    fun `equal victory points are tie-broken by tichu points, then by team id`() {
        // All teams on VP 2; TP order w(10) > x(5) = y(5) > z(0); x before y by id.
        val standings = listOf(
            PairingEntry(TeamId("z"), victoryPoints = 2, tichuPoints = 0),
            PairingEntry(TeamId("y"), victoryPoints = 2, tichuPoints = 5),
            PairingEntry(TeamId("w"), victoryPoints = 2, tichuPoints = 10),
            PairingEntry(TeamId("x"), victoryPoints = 2, tichuPoints = 5),
        )

        val pairing = SwissPairing.pairNextRound(standings, PairingHistory(emptyList()))

        assertEquals(
            listOf(
                Pair(TeamId("w"), TeamId("x")),
                Pair(TeamId("y"), TeamId("z")),
            ),
            pairing.tables.map { Pair(it.home, it.away) },
        )
    }

    @Test
    fun `next round avoids rematches by swapping within the standings order`() {
        // Round 1 was (a,b) and (c,d); all teams on equal VP. Adjacent pairing would
        // repeat it, so the component must find a rematch-free alternative.
        val standings = listOf(
            PairingEntry(TeamId("a"), victoryPoints = 2, tichuPoints = 0),
            PairingEntry(TeamId("b"), victoryPoints = 2, tichuPoints = 0),
            PairingEntry(TeamId("c"), victoryPoints = 2, tichuPoints = 0),
            PairingEntry(TeamId("d"), victoryPoints = 2, tichuPoints = 0),
        )
        val history = PairingHistory(listOf(Pair(TeamId("a"), TeamId("b")), Pair(TeamId("c"), TeamId("d"))))

        val pairing = SwissPairing.pairNextRound(standings, history)

        val pairs = pairing.tables.map { Pair(it.home, it.away) }
        assertTrue(
            history.pairings.none { it in pairs },
            "pairing repeated a previous matchup: $pairs",
        )
        // Still a complete pairing of all teams.
        assertEquals(standings.map { it.teamId }.toSet(), pairs.flatMap { listOf(it.first, it.second) }.toSet())
    }

    @Test
    fun `next round finds a rematch-free pairing beyond a simple swap`() {
        // History holds (a,b), (c,d), (a,c), (b,d). The only rematch-free pairing is
        // (a,d) + (b,c), which the search must find.
        val standings = listOf(
            PairingEntry(TeamId("a"), victoryPoints = 2, tichuPoints = 0),
            PairingEntry(TeamId("b"), victoryPoints = 2, tichuPoints = 0),
            PairingEntry(TeamId("c"), victoryPoints = 2, tichuPoints = 0),
            PairingEntry(TeamId("d"), victoryPoints = 2, tichuPoints = 0),
        )
        val history = PairingHistory(
            listOf(
                Pair(TeamId("a"), TeamId("b")),
                Pair(TeamId("c"), TeamId("d")),
                Pair(TeamId("a"), TeamId("c")),
                Pair(TeamId("b"), TeamId("d")),
            ),
        )

        val pairing = SwissPairing.pairNextRound(standings, history)

        val pairs = pairing.tables.map { Pair(it.home, it.away) }
        assertEquals(emptyList(), pairs.filter { (a, b) -> history.hasRematch(a, b) })
    }

    @Test
    fun `when no rematch-free pairing exists, the one with the fewest rematches wins`() {
        // All six pairs of four teams have already played: every possible round has
        // exactly two rematches; the pairing must accept them deterministically.
        val standings = listOf(
            PairingEntry(TeamId("a"), victoryPoints = 2, tichuPoints = 0),
            PairingEntry(TeamId("b"), victoryPoints = 2, tichuPoints = 0),
            PairingEntry(TeamId("c"), victoryPoints = 2, tichuPoints = 0),
            PairingEntry(TeamId("d"), victoryPoints = 2, tichuPoints = 0),
        )
        val teams = listOf(TeamId("a"), TeamId("b"), TeamId("c"), TeamId("d"))
        val history = PairingHistory(
            teams.flatMap { first -> teams.filter { it != first }.map { Pair(first, it) } }.distinct(),
        )

        val pairing = SwissPairing.pairNextRound(standings, history)
        val again = SwissPairing.pairNextRound(standings, history)

        val pairs = pairing.tables.map { Pair(it.home, it.away) }
        assertEquals(2, pairs.count { (a, b) -> history.hasRematch(a, b) })
        assertEquals(standings.map { it.teamId }.toSet(), pairs.flatMap { listOf(it.first, it.second) }.toSet())
        assertEquals(pairing, again, "same input must produce the same pairing")
    }

    @Test
    fun `odd team count is rejected in the next round too`() {
        val standings = (1..5).map { PairingEntry(TeamId("t$it"), 0, 0) }

        assertFailsWith<PairingUnsupportedException> {
            SwissPairing.pairNextRound(standings, PairingHistory(emptyList()))
        }
    }

    @Test
    fun `next round takes the pairing with the fewest rematches when avoidance fails`() {
        // Six teams; every pair has already played except (a,b) and (c,d). No
        // rematch-free round exists: the minimum is exactly 1 rematch, achieved
        // only by (a,b) + (c,d) + (e,f). A non-minimizing pairing would carry 2+.
        val standings = (0 until 6).map { PairingEntry(TeamId("abcdef"[it].toString()), 2, 0) }
        val teams = teamIds("a", "b", "c", "d", "e", "f")
        val free = setOf(setOf(TeamId("a"), TeamId("b")), setOf(TeamId("c"), TeamId("d")))
        val history = PairingHistory(
            teams.flatMap { first ->
                teams.filter { it != first }.map { Pair(first, it) }
            }.filter { setOf(it.first, it.second) !in free }.distinct(),
        )

        val pairing = SwissPairing.pairNextRound(standings, history)

        val pairs = pairing.tables.map { Pair(it.home, it.away) }
        assertEquals(1, pairs.count { (a, b) -> history.hasRematch(a, b) })
        assertEquals(
            setOf(setOf(TeamId("a"), TeamId("b")), setOf(TeamId("c"), TeamId("d")), setOf(TeamId("e"), TeamId("f"))),
            pairs.map { setOf(it.first, it.second) }.toSet(),
        )
    }
}
