package tichu.domain

import tichu.testsupport.teamOf
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for the team-only tournament statistics (GLOSSARY.md: **Team
 * Statistics**, **Successful Tichu Call**, **Bomb Count**, **Double Win**), derived
 * from the games of completed tables only.
 *
 * Scenario (hand-worked, base scores sum to 100 unless a double win forces 0/0):
 * - Round 1, normal tables: [a, b] and [c, d]; both completed.
 * - Round 2, trio table [a, b, e] (seat order a, b, e; schedule AB, AE, BE, AB, AE, BE)
 *   completed, and table [c, d] not completed (only c confirmed).
 * - Round 3, table [a, b] with a game entered but no confirmations at all.
 * - Team f is seated nowhere.
 * Only the completed rounds' games count; each team's games per round: 4.
 */
class TeamStatisticsTest {

    private val tournamentId = TournamentId("t-1")
    private val settings = TournamentSettings()

    private fun round(number: Int, vararg tables: List<String>) = Round(
        id = RoundId("r-$number"),
        tournamentId = tournamentId,
        number = number,
        pairing = Pairing(tables.mapIndexed { i, seated -> Table(TableId(i + 1), seated.map(::TeamId)) }),
    )

    private fun completed(tableNumber: Int, vararg teams: String) = TableId(tableNumber) to teams.map(::TeamId).toSet()

    /** One player of [team] in seat [seat]: finish [position], [call] made, [bombs] played. */
    private fun p(
        team: String,
        seat: Int,
        position: Int?,
        call: TichuCall = TichuCall.NONE,
        bombs: Int = 0,
    ) = PlayerResult(PlayerId("$team-$seat"), position, call, bombs)

    private fun side(team: String, base: Int, first: PlayerResult, second: PlayerResult) =
        TeamResult(TeamId(team), base, listOf(first, second))

    private fun game(roundNumber: Int, tableNumber: Int, gameNumber: Int, first: TeamResult, second: TeamResult): Game =
        Game.record(
            GameKey(tournamentId, roundNumber, tableNumber, gameNumber),
            listOf(first, second),
            beschiss = false,
            settings = settings,
        )

    private val teams = listOf("a", "b", "c", "d", "e", "f").map { teamOf(tournamentId, it) }

    private val round1 = PlayedRound(
        round = round(1, listOf("a", "b"), listOf("c", "d")),
        games = listOf(
            // Table 1, a vs b.
            game(1, 1, 1, side("a", 60, p("a", 1, 1, TichuCall.SMALL), p("a", 2, 3, bombs = 1)),
                side("b", 40, p("b", 1, 2, bombs = 2), p("b", 2, 4, TichuCall.GRAND))),
            // Double win b: a's SMALL call fails (no position); b's GRAND call succeeds.
            game(1, 1, 2, side("a", 0, p("a", 1, null, bombs = 1), p("a", 2, null, TichuCall.SMALL)),
                side("b", 0, p("b", 1, 1, TichuCall.GRAND), p("b", 2, 2, bombs = 1))),
            game(1, 1, 3, side("a", 70, p("a", 1, 1, TichuCall.GRAND), p("a", 2, 3)),
                side("b", 30, p("b", 1, 2, TichuCall.SMALL), p("b", 2, 4, bombs = 3))),
            game(1, 1, 4, side("a", 45, p("a", 1, 2), p("a", 2, 4)),
                side("b", 55, p("b", 1, 1, TichuCall.SMALL), p("b", 2, 3))),
            // Table 2, c vs d.
            game(1, 2, 1, side("c", 50, p("c", 1, 1, TichuCall.SMALL, bombs = 1), p("c", 2, 3)),
                side("d", 50, p("d", 1, 2), p("d", 2, 4, bombs = 2))),
            // Double win d: c's SMALL call fails (no position); c holds 3 bombs.
            game(1, 2, 2, side("c", 0, p("c", 1, null, TichuCall.SMALL), p("c", 2, null, bombs = 3)),
                side("d", 0, p("d", 1, 1), p("d", 2, 2))),
            game(1, 2, 3, side("c", 100, p("c", 1, 1), p("c", 2, 3, TichuCall.GRAND)),
                side("d", 0, p("d", 1, 2), p("d", 2, 4, TichuCall.SMALL))),
            game(1, 2, 4, side("c", 40, p("c", 1, 2), p("c", 2, 4)),
                side("d", 60, p("d", 1, 1, bombs = 1), p("d", 2, 3))),
        ),
        confirmations = mapOf(completed(1, "a", "b"), completed(2, "c", "d")),
    )

    private val round2 = PlayedRound(
        round = round(2, listOf("a", "b", "e"), listOf("c", "d")),
        games = listOf(
            // Trio table 1: a vs b.
            game(2, 1, 1, side("a", 50, p("a", 1, 1, TichuCall.SMALL), p("a", 2, 3, bombs = 2)),
                side("b", 50, p("b", 1, 2, bombs = 1), p("b", 2, 4))),
            // Trio table 1: a vs e; e's GRAND call succeeds, a's SMALL call fails (pos 4).
            game(2, 1, 2, side("a", 30, p("a", 1, 2), p("a", 2, 4, TichuCall.SMALL, bombs = 1)),
                side("e", 70, p("e", 1, 1, TichuCall.GRAND), p("e", 2, 3, bombs = 1))),
            // Trio table 1: b vs e; b's SMALL call fails (pos 3).
            game(2, 1, 3, side("b", 60, p("b", 1, 1), p("b", 2, 3, TichuCall.SMALL)),
                side("e", 40, p("e", 1, 2), p("e", 2, 4, bombs = 2))),
            // Trio table 1: a vs b; double win a, with b's two bombs.
            game(2, 1, 4, side("a", 0, p("a", 1, 1, TichuCall.SMALL, bombs = 1), p("a", 2, 2)),
                side("b", 0, p("b", 1, null, bombs = 2), p("b", 2, null))),
            // Trio table 1: a vs e.
            game(2, 1, 5, side("a", 50, p("a", 1, 2), p("a", 2, 4)),
                side("e", 50, p("e", 1, 1), p("e", 2, 3, bombs = 3))),
            // Trio table 1: b vs e; e's SMALL call succeeds.
            game(2, 1, 6, side("b", 20, p("b", 1, 2), p("b", 2, 3, bombs = 1)),
                side("e", 80, p("e", 1, 1, TichuCall.SMALL), p("e", 2, 4))),
            // Table 2 (c vs d) is not completed: its 3 bombs of c must not count.
            game(2, 2, 1, side("c", 50, p("c", 1, 1, TichuCall.SMALL, bombs = 3), p("c", 2, 3)),
                side("d", 50, p("d", 1, 2), p("d", 2, 4))),
        ),
        confirmations = mapOf(completed(1, "a", "b", "e"), TableId(2) to setOf(TeamId("c"))),
    )

    /** Round 3 has a game entered, but no confirmations: nothing of it counts. */
    private val round3 = PlayedRound(
        round = round(3, listOf("a", "b")),
        games = listOf(
            game(3, 1, 1, side("a", 50, p("a", 1, 1, TichuCall.SMALL, bombs = 3), p("a", 2, 3)),
                side("b", 50, p("b", 1, 2), p("b", 2, 4))),
        ),
        confirmations = emptyMap(),
    )

    @Test
    fun `team statistics count successful calls, bombs, bombs per game and double wins over completed tables only`() {
        val stats = TeamStatistics.compute(teams, listOf(round1, round2, round3), settings)

        // a: small 1 (R1) + 2 (R2 games 1 and 4) = 3; grand 1 (R1 game 3); bombs 2 + 4 = 6;
        //    seated in 2 completed rounds = 8 games, 6 / 8 = 0.75; double win in R2 game 4.
        // b: small 0 + 1 (R1 game 4) = 1; grand 1 (R1 game 2); bombs 6 + 4 = 10; 10 / 8 = 1.25;
        //    double win in R1 game 2.
        // c: small 1 (R1 table 2 game 1); grand 0; bombs 1 + 3 = 4; one completed round = 4 games, 1.0.
        // d: small 0; grand 0; bombs 2 + 1 = 3; one completed round = 4 games, 0.75; double win in R1 table 2 game 2.
        // e: small 1 (R2 game 6); grand 1 (R2 game 2); bombs 1 + 2 + 3 = 6; one completed round, 6 / 4 = 1.5.
        // f: nothing, no completed round.
        assertEquals(
            listOf(
                TeamStatistics(TeamId("a"), successfulSmallTichu = 3, successfulGrandTichu = 1, bombCount = 6, bombsPerGame = 0.75, doubleWins = 1),
                TeamStatistics(TeamId("b"), successfulSmallTichu = 1, successfulGrandTichu = 1, bombCount = 10, bombsPerGame = 1.25, doubleWins = 1),
                TeamStatistics(TeamId("c"), successfulSmallTichu = 1, successfulGrandTichu = 0, bombCount = 4, bombsPerGame = 1.0, doubleWins = 0),
                TeamStatistics(TeamId("d"), successfulSmallTichu = 0, successfulGrandTichu = 0, bombCount = 3, bombsPerGame = 0.75, doubleWins = 1),
                TeamStatistics(TeamId("e"), successfulSmallTichu = 1, successfulGrandTichu = 1, bombCount = 6, bombsPerGame = 1.5, doubleWins = 0),
                TeamStatistics(TeamId("f"), successfulSmallTichu = 0, successfulGrandTichu = 0, bombCount = 0, bombsPerGame = null, doubleWins = 0),
            ),
            stats,
        )
    }

    @Test
    fun `a team seated in no completed round has no bombs per game`() {
        val stats = TeamStatistics.compute(teams, listOf(round3), settings)

        assertEquals(null, stats.single { it.teamId == TeamId("a") }.bombsPerGame)
    }
}
