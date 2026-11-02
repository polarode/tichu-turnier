package com.example.template.domain

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

        val paired = pairing.tables.flatMap { it.seatedTeams }
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

    @ParameterizedTest
    @ValueSource(ints = [3, 5, 7, 9, 11])
    fun `odd first round seats exactly one trio table, last, after the normal tables`(count: Int) {
        val teams = (1..count).map { TeamId("t$it") }

        val pairing = SwissPairing.pairFirstRound(teams, Random(count))

        assertEquals((1..(count - 1) / 2).toList(), pairing.tables.map { it.id.value })
        assertEquals(listOf(true), pairing.tables.filter { it.isTrio }.map { pairing.tables.last() == it })
        assertEquals(teams.toSet(), pairing.tables.flatMap { it.seatedTeams }.toSet())
        assertEquals(count, pairing.tables.sumOf { it.seatedTeams.size })
    }

    @ParameterizedTest
    @ValueSource(ints = [2, 4, 6, 8])
    fun `even first round has no trio table`(count: Int) {
        val teams = (1..count).map { TeamId("t$it") }

        val pairing = SwissPairing.pairFirstRound(teams, Random(count))

        assertEquals(count / 2, pairing.tables.size)
        assertTrue(pairing.tables.none { it.isTrio })
    }

    @Test
    fun `odd first round seats the last three shuffled teams at the trio table`() {
        val teams = teamIds("a", "b", "c", "d", "e")

        val pairing = SwissPairing.pairFirstRound(teams, Random(7))

        val shuffled = teams.shuffled(Random(7))
        assertEquals(
            listOf(shuffled.take(2), shuffled.drop(2)),
            pairing.tables.map { it.seatedTeams },
        )
    }

    @Test
    fun `next round pairs teams sorted by victory points, then tichu points, then id`() {
        val standings = listOf(
            PairingEntry(TeamId("d"), victoryPoints = 1, tichuPoints = 99),
            PairingEntry(TeamId("c"), victoryPoints = 2, tichuPoints = 0),
            PairingEntry(TeamId("b"), victoryPoints = 3, tichuPoints = 0),
            PairingEntry(TeamId("a"), victoryPoints = 4, tichuPoints = 0),
        )

        val pairing = SwissPairing.pairNextRound(standings, PairingHistory(emptyList()), Random(1))

        assertEquals(
            listOf(
                Pair(TeamId("a"), TeamId("b")),
                Pair(TeamId("c"), TeamId("d")),
            ),
            pairing.tables.map { Pair(it.seatedTeams[0], it.seatedTeams[1]) },
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

        val pairing = SwissPairing.pairNextRound(standings, PairingHistory(emptyList()), Random(1))

        assertEquals(
            listOf(
                Pair(TeamId("w"), TeamId("x")),
                Pair(TeamId("y"), TeamId("z")),
            ),
            pairing.tables.map { Pair(it.seatedTeams[0], it.seatedTeams[1]) },
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

        val pairing = SwissPairing.pairNextRound(standings, history, Random(1))

        val pairs = pairing.tables.map { Pair(it.seatedTeams[0], it.seatedTeams[1]) }
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

        val pairing = SwissPairing.pairNextRound(standings, history, Random(1))

        val pairs = pairing.tables.map { Pair(it.seatedTeams[0], it.seatedTeams[1]) }
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

        val pairing = SwissPairing.pairNextRound(standings, history, Random(1))
        val again = SwissPairing.pairNextRound(standings, history, Random(1))

        val pairs = pairing.tables.map { Pair(it.seatedTeams[0], it.seatedTeams[1]) }
        assertEquals(2, pairs.count { (a, b) -> history.hasRematch(a, b) })
        assertEquals(standings.map { it.teamId }.toSet(), pairs.flatMap { listOf(it.first, it.second) }.toSet())
        assertEquals(pairing, again, "same input must produce the same pairing")
    }

    @Test
    fun `odd next round seats the bottom three in standings order at the last table`() {
        val standings = listOf(
            PairingEntry(TeamId("e"), victoryPoints = 0, tichuPoints = 0),
            PairingEntry(TeamId("c"), victoryPoints = 4, tichuPoints = 0),
            PairingEntry(TeamId("a"), victoryPoints = 8, tichuPoints = 0),
            PairingEntry(TeamId("d"), victoryPoints = 2, tichuPoints = 0),
            PairingEntry(TeamId("b"), victoryPoints = 6, tichuPoints = 0),
        )

        val pairing = SwissPairing.pairNextRound(standings, PairingHistory(emptyList()), Random(1))

        assertEquals(
            listOf(teamIds("a", "b"), teamIds("c", "d", "e")),
            pairing.tables.map { it.seatedTeams },
        )
        assertEquals(listOf(1, 2), pairing.tables.map { it.id.value })
    }

    @ParameterizedTest
    @ValueSource(ints = [3, 5, 7, 9])
    fun `odd next round seats exactly one trio table, last`(count: Int) {
        val standings = (1..count).map { PairingEntry(TeamId("t$it"), victoryPoints = 2 * it, tichuPoints = 0) }

        val pairing = SwissPairing.pairNextRound(standings, PairingHistory(emptyList()), Random(1))

        assertEquals((1..(count - 1) / 2).toList(), pairing.tables.map { it.id.value })
        assertTrue(pairing.tables.last().isTrio)
        assertEquals(1, pairing.tables.count { it.isTrio })
        assertEquals(teamIds("t3", "t2", "t1"), pairing.tables.last().seatedTeams)
    }

    @ParameterizedTest
    @ValueSource(ints = [2, 4, 6, 8])
    fun `even next round has no trio table and ignores the random source`(count: Int) {
        val standings = (1..count).map { PairingEntry(TeamId("t$it"), victoryPoints = 2, tichuPoints = 0) }

        val pairing = SwissPairing.pairNextRound(standings, PairingHistory(emptyList()), Random(1))
        val otherSeed = SwissPairing.pairNextRound(standings, PairingHistory(emptyList()), Random(99))

        assertTrue(pairing.tables.none { it.isTrio })
        assertEquals(pairing, otherSeed)
    }

    @Test
    fun `the trio takes the bottom three even when they have all met before`() {
        val standings = listOf(
            PairingEntry(TeamId("a"), victoryPoints = 8, tichuPoints = 0),
            PairingEntry(TeamId("b"), victoryPoints = 6, tichuPoints = 0),
            PairingEntry(TeamId("c"), victoryPoints = 4, tichuPoints = 0),
            PairingEntry(TeamId("d"), victoryPoints = 2, tichuPoints = 0),
            PairingEntry(TeamId("e"), victoryPoints = 0, tichuPoints = 0),
        )
        val history = PairingHistory.of(listOf(Table(TableId(1), teamIds("c", "d", "e"))))

        val pairing = SwissPairing.pairNextRound(standings, history, Random(1))

        assertEquals(teamIds("c", "d", "e"), pairing.tables.last().seatedTeams)
    }

    @Test
    fun `teams tied exactly across the trio cut-off are drawn at random`() {
        // b, c and d tie on VP and TP; e is clearly last. The trio is e plus two of
        // b/c/d, drawn by the injected random; the third pairs with a.
        val standings = listOf(
            PairingEntry(TeamId("a"), victoryPoints = 8, tichuPoints = 0),
            PairingEntry(TeamId("b"), victoryPoints = 4, tichuPoints = 10),
            PairingEntry(TeamId("c"), victoryPoints = 4, tichuPoints = 10),
            PairingEntry(TeamId("d"), victoryPoints = 4, tichuPoints = 10),
            PairingEntry(TeamId("e"), victoryPoints = 0, tichuPoints = 0),
        )

        val partnersOfA = (0 until 30).map { seed ->
            val pairing = SwissPairing.pairNextRound(standings, PairingHistory(emptyList()), Random(seed))
            val trio = pairing.tables.last().seatedTeams
            assertEquals(TeamId("e"), trio.last(), "the clear last team always sits in the trio, seat C")
            assertEquals(pairing, SwissPairing.pairNextRound(standings, PairingHistory(emptyList()), Random(seed)))
            pairing.tables.first().seatedTeams.single { it != TeamId("a") }
        }

        assertEquals(teamIds("b", "c", "d").toSet(), partnersOfA.toSet(), "every tied team is drawn out at some seed")
    }

    @Test
    fun `a tie entirely below the cut-off draws nothing`() {
        // c, d, e tie but all three fall into the trio anyway; b is clearly above.
        val standings = listOf(
            PairingEntry(TeamId("a"), victoryPoints = 8, tichuPoints = 0),
            PairingEntry(TeamId("b"), victoryPoints = 6, tichuPoints = 0),
            PairingEntry(TeamId("c"), victoryPoints = 2, tichuPoints = 0),
            PairingEntry(TeamId("d"), victoryPoints = 2, tichuPoints = 0),
            PairingEntry(TeamId("e"), victoryPoints = 2, tichuPoints = 0),
        )

        val trios = (0 until 10).map { seed ->
            SwissPairing.pairNextRound(standings, PairingHistory(emptyList()), Random(seed)).tables.last().seatedTeams
        }

        assertEquals(setOf(teamIds("c", "d", "e")), trios.toSet())
    }

    @Test
    fun `odd next round still avoids rematches among the normal tables`() {
        val standings = listOf(
            PairingEntry(TeamId("a"), victoryPoints = 8, tichuPoints = 0),
            PairingEntry(TeamId("b"), victoryPoints = 6, tichuPoints = 0),
            PairingEntry(TeamId("c"), victoryPoints = 5, tichuPoints = 0),
            PairingEntry(TeamId("d"), victoryPoints = 4, tichuPoints = 0),
            PairingEntry(TeamId("e"), victoryPoints = 2, tichuPoints = 0),
            PairingEntry(TeamId("f"), victoryPoints = 1, tichuPoints = 0),
            PairingEntry(TeamId("g"), victoryPoints = 0, tichuPoints = 0),
        )
        val history = PairingHistory.of(
            listOf(Table(TableId(1), teamIds("a", "b")), Table(TableId(2), teamIds("c", "d"))),
        )

        val pairing = SwissPairing.pairNextRound(standings, history, Random(1))

        assertEquals(
            listOf(teamIds("a", "c"), teamIds("b", "d"), teamIds("e", "f", "g")),
            pairing.tables.map { it.seatedTeams },
        )
    }

    @Test
    fun `every pair of a trio table counts as having met`() {
        val history = PairingHistory.of(
            listOf(Table(TableId(1), teamIds("a", "b")), Table(TableId(2), teamIds("c", "d", "e"))),
        )

        assertTrue(history.hasRematch(TeamId("a"), TeamId("b")))
        assertTrue(history.hasRematch(TeamId("c"), TeamId("d")))
        assertTrue(history.hasRematch(TeamId("e"), TeamId("c")))
        assertTrue(history.hasRematch(TeamId("d"), TeamId("e")))
        assertFalse(history.hasRematch(TeamId("a"), TeamId("c")))
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

        val pairing = SwissPairing.pairNextRound(standings, history, Random(1))

        val pairs = pairing.tables.map { Pair(it.seatedTeams[0], it.seatedTeams[1]) }
        assertEquals(1, pairs.count { (a, b) -> history.hasRematch(a, b) })
        assertEquals(
            setOf(setOf(TeamId("a"), TeamId("b")), setOf(TeamId("c"), TeamId("d")), setOf(TeamId("e"), TeamId("f"))),
            pairs.map { setOf(it.first, it.second) }.toSet(),
        )
    }
}
