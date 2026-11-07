package tichu.domain

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for the pure Table Totals computation (GLOSSARY.md: **Table Totals**):
 * per completed table of a round, each seated team's summed VP and TP over the
 * games it played there.
 */
class TableTotalsTest {

    private val tournamentId = TournamentId("t-1")
    private val settings = TournamentSettings()

    private fun round(number: Int, vararg tables: List<String>) = Round(
        id = RoundId("r-$number"),
        tournamentId = tournamentId,
        number = number,
        pairing = Pairing(tables.mapIndexed { i, seated -> Table(TableId(i + 1), seated.map(::TeamId)) }),
    )

    /**
     * A plain game: [first] takes [firstBase] of the 100 base points, positions
     * 1 and 3 when it wins (4 and 2 otherwise), no calls, no bombs. VP 2/0, or
     * 1/1 on a 50/50 tie.
     */
    private fun game(roundNumber: Int, tableNumber: Int, gameNumber: Int, first: String, second: String, firstBase: Int): Game {
        val firstWins = firstBase >= 50
        return Game.record(
            GameKey(tournamentId, roundNumber, tableNumber, gameNumber),
            listOf(
                TeamResult(TeamId(first), firstBase, listOf(
                    PlayerResult(PlayerId("$first-1"), if (firstWins) 1 else 2, TichuCall.NONE, 0),
                    PlayerResult(PlayerId("$first-2"), if (firstWins) 3 else 4, TichuCall.NONE, 0),
                )),
                TeamResult(TeamId(second), 100 - firstBase, listOf(
                    PlayerResult(PlayerId("$second-1"), if (firstWins) 2 else 1, TichuCall.NONE, 0),
                    PlayerResult(PlayerId("$second-2"), if (firstWins) 4 else 3, TichuCall.NONE, 0),
                )),
            ),
            beschiss = false,
            settings = settings,
        )
    }

    /** Four games at a normal table, [first] taking the given base scores. */
    private fun normalTable(roundNumber: Int, tableNumber: Int, first: String, second: String, vararg firstBases: Int) =
        firstBases.mapIndexed { i, base -> game(roundNumber, tableNumber, i + 1, first, second, base) }

    private fun completed(tableNumber: Int, vararg teams: String) = TableId(tableNumber) to teams.map(::TeamId).toSet()

    @Test
    fun `a completed table sums each team's victory and tichu points over its games`() {
        val played = PlayedRound(
            round = round(1, listOf("a", "b")),
            games = normalTable(1, 1, "a", "b", 60, 70, 50, 20),
            confirmations = mapOf(completed(1, "a", "b")),
        )

        val totals = TableTotals.of(played, settings)

        // a: wins 60, 70 (4 VP), ties 50 (1 VP), loses 20 → 5 VP, 200 TP; b: 1 + 2 = 3 VP, 200 TP.
        assertEquals(
            mapOf(
                TableId(1) to mapOf(
                    TeamId("a") to TableTotal(victoryPoints = 5, tichuPoints = 200),
                    TeamId("b") to TableTotal(victoryPoints = 3, tichuPoints = 200),
                ),
            ),
            totals,
        )
    }

    @Test
    fun `a trio team's totals cover only the games it played`() {
        // Trio schedule: 1 CD, 2 CE, 3 DE, 4 CD, 5 CE, 6 DE (seat order c, d, e).
        val played = PlayedRound(
            round = round(1, listOf("c", "d", "e")),
            games = listOf(
                game(1, 1, 1, "c", "d", 60), // c wins: c 2 VP / 60 TP; d 0 VP / 40 TP
                game(1, 1, 2, "c", "e", 30), // e wins: c 0 VP / 30 TP; e 2 VP / 70 TP
                game(1, 1, 3, "d", "e", 50), // tie:    d 1 VP / 50 TP; e 1 VP / 50 TP
                game(1, 1, 4, "c", "d", 80), // c wins: c 2 VP / 80 TP; d 0 VP / 20 TP
                game(1, 1, 5, "c", "e", 50), // tie:    c 1 VP / 50 TP; e 1 VP / 50 TP
                game(1, 1, 6, "d", "e", 90), // d wins: d 2 VP / 90 TP; e 0 VP / 10 TP
            ),
            confirmations = mapOf(completed(1, "c", "d", "e")),
        )

        val totals = TableTotals.of(played, settings)

        // c plays 1, 2, 4, 5: 2+0+2+1 = 5 VP, 60+30+80+50 = 220 TP.
        // d plays 1, 3, 4, 6: 0+1+0+2 = 3 VP, 40+50+20+90 = 200 TP.
        // e plays 2, 3, 5, 6: 2+1+1+0 = 4 VP, 70+50+50+10 = 180 TP.
        assertEquals(
            mapOf(
                TableId(1) to mapOf(
                    TeamId("c") to TableTotal(victoryPoints = 5, tichuPoints = 220),
                    TeamId("d") to TableTotal(victoryPoints = 3, tichuPoints = 200),
                    TeamId("e") to TableTotal(victoryPoints = 4, tichuPoints = 180),
                ),
            ),
            totals,
        )
    }

    @Test
    fun `a table that is not fully confirmed is absent from the totals`() {
        val played = PlayedRound(
            round = round(1, listOf("a", "b"), listOf("c", "d")),
            games = normalTable(1, 1, "a", "b", 60, 60, 60, 60) + normalTable(1, 2, "c", "d", 60, 60, 60, 60),
            // Table 1 has only a confirmed (Confirming, not completed); table 2 is completed.
            confirmations = mapOf(TableId(1) to setOf(TeamId("a")), completed(2, "c", "d")),
        )

        val totals = TableTotals.of(played, settings)

        // Only table 2 appears: c wins all four (8 VP, 240 TP), d loses all four (0 VP, 160 TP).
        assertEquals(
            mapOf(
                TableId(2) to mapOf(
                    TeamId("c") to TableTotal(victoryPoints = 8, tichuPoints = 240),
                    TeamId("d") to TableTotal(victoryPoints = 0, tichuPoints = 160),
                ),
            ),
            totals,
        )
    }
}
