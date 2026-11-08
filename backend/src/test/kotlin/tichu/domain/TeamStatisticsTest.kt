package tichu.domain

import tichu.testsupport.teamOf
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for the team-only tournament statistics (GLOSSARY.md: **Team
 * Statistics**, **Successful Tichu Call**, **Bomb Count**, **Double Win**, **Tichu
 * Calls Made**, **Destroyed Tichu**, **Beschiss Games**, **Good Horse Ratio**), derived
 * from the games of completed tables only.
 *
 * Scenario (hand-worked, base scores sum to 100 unless a double win forces 0/0):
 * - Round 1, normal tables: [a, b] and [c, d]; both completed.
 * - Round 2, trio table [a, b, e] (seat order a, b, e; schedule AB, AE, BE, AB, AE, BE)
 *   completed, and table [c, d] not completed (only c confirmed).
 * - Round 3, table [a, b] with a game entered but no confirmations at all.
 * - Team f is seated nowhere.
 * Only the completed rounds' games count; each team's games per round: 4.
 *
 * Tichu Points (TP) and Victory Points (VP) use the default settings: grand Tichu
 * 200, small Tichu 100, double win 200, and VP 3 for a double win, 2 for the higher
 * TP, 1 each on a tie, 0 otherwise.
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

    private fun game(
        roundNumber: Int,
        tableNumber: Int,
        gameNumber: Int,
        first: TeamResult,
        second: TeamResult,
        beschiss: Boolean = false,
    ): Game =
        Game.record(
            GameKey(tournamentId, roundNumber, tableNumber, gameNumber),
            listOf(first, second),
            beschiss = beschiss,
            settings = settings,
        )

    /** A round 1 of the tables [a, b] (completed) with [games]. */
    private fun completedRound(vararg games: Game) = PlayedRound(
        round = round(1, listOf("a", "b")),
        games = games.toList(),
        confirmations = mapOf(completed(1, "a", "b")),
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
        // Tichu Calls Made, Destroyed Tichus, Beschiss Games and Good Horse Ratio, hand-worked per game:
        // a: calls 3 (R1) + 3 (R2) = 6; destroyed 2 (R1 games 1 and 3, the b calls failing to a's first out);
        //    TP 375 (R1) + 430 (R2) = 805, VP 4 + 6 = 10, ratio 80.5.
        // b: calls 4 (R1) + 1 (R2) = 5; destroyed 1 (R1 game 2); TP 325 + 30 = 355, VP 5 + 0 = 5, ratio 71.0.
        // c: calls 3 (R1 table 2); destroyed 1 (R1 game 7); TP 150 - 100 - 100 + 40 = -10, VP 2 + 0 + 1 + 0 = 3, ratio -10 / 3.
        // d: calls 1 (R1 game 7); destroyed 1 (R1 game 6); TP 50 + 200 - 100 + 60 = 210, VP 0 + 3 + 1 + 2 = 6, ratio 35.0.
        // e: calls 2 (R2 games 2 and 6); destroyed 1 (R2 game 2); TP 270 + 40 + 50 + 180 = 540, VP 2 + 2 + 1 + 2 = 7, ratio 540 / 7.
        // f: nothing, ratio null (no VP). No game is flagged Beschiss.
        assertEquals(
            listOf(
                TeamStatistics(TeamId("a"), successfulSmallTichu = 3, successfulGrandTichu = 1, bombCount = 6, bombsPerGame = 0.75, doubleWins = 1,
                    tichuCalls = 6, destroyedTichus = 2, beschissGames = 0, goodHorseRatio = 80.5),
                TeamStatistics(TeamId("b"), successfulSmallTichu = 1, successfulGrandTichu = 1, bombCount = 10, bombsPerGame = 1.25, doubleWins = 1,
                    tichuCalls = 5, destroyedTichus = 1, beschissGames = 0, goodHorseRatio = 71.0),
                TeamStatistics(TeamId("c"), successfulSmallTichu = 1, successfulGrandTichu = 0, bombCount = 4, bombsPerGame = 1.0, doubleWins = 0,
                    tichuCalls = 3, destroyedTichus = 1, beschissGames = 0, goodHorseRatio = -10.0 / 3),
                TeamStatistics(TeamId("d"), successfulSmallTichu = 0, successfulGrandTichu = 0, bombCount = 3, bombsPerGame = 0.75, doubleWins = 1,
                    tichuCalls = 1, destroyedTichus = 1, beschissGames = 0, goodHorseRatio = 35.0),
                TeamStatistics(TeamId("e"), successfulSmallTichu = 1, successfulGrandTichu = 1, bombCount = 6, bombsPerGame = 1.5, doubleWins = 0,
                    tichuCalls = 2, destroyedTichus = 1, beschissGames = 0, goodHorseRatio = 540.0 / 7),
                TeamStatistics(TeamId("f"), successfulSmallTichu = 0, successfulGrandTichu = 0, bombCount = 0, bombsPerGame = null, doubleWins = 0,
                    tichuCalls = 0, destroyedTichus = 0, beschissGames = 0, goodHorseRatio = null),
            ),
            stats,
        )
    }

    @Test
    fun `a team seated in no completed round has no bombs per game`() {
        val stats = TeamStatistics.compute(teams, listOf(round3), settings)

        assertEquals(null, stats.single { it.teamId == TeamId("a") }.bombsPerGame)
    }

    @Test
    fun `a small Tichu that fails to a team's first finish is destroyed by that team`() {
        // a: 60 base, a1 finishes first without a call: TP 60, VP 2.
        // b: 40 base, b1 SMALL call finishes second: TP 40 - 100 = -60, VP 0.
        val stats = TeamStatistics.compute(teams, listOf(completedRound(
            game(1, 1, 1, side("a", 60, p("a", 1, 1), p("a", 2, 3)), side("b", 40, p("b", 1, 2, TichuCall.SMALL), p("b", 2, 4))),
        )), settings)

        val a = stats.single { it.teamId == TeamId("a") }
        val b = stats.single { it.teamId == TeamId("b") }
        assertEquals(0, a.tichuCalls)
        assertEquals(1, a.destroyedTichus)
        assertEquals(30.0, a.goodHorseRatio)
        assertEquals(1, b.tichuCalls)
        assertEquals(0, b.destroyedTichus)
        assertEquals(null, b.goodHorseRatio)
    }

    @Test
    fun `both failing Tichu calls of a team are destroyed when the other team holds first place`() {
        // a: 60 base, a1 finishes first without a call: TP 60, VP 2.
        // b: 40 base, b1 SMALL (2nd) and b2 GRAND (4th) both fail: TP 40 - 100 - 200 = -260, VP 0.
        val stats = TeamStatistics.compute(teams, listOf(completedRound(
            game(1, 1, 1, side("a", 60, p("a", 1, 1), p("a", 2, 3)),
                side("b", 40, p("b", 1, 2, TichuCall.SMALL), p("b", 2, 4, TichuCall.GRAND))),
        )), settings)

        val a = stats.single { it.teamId == TeamId("a") }
        val b = stats.single { it.teamId == TeamId("b") }
        assertEquals(2, a.destroyedTichus)
        assertEquals(0, b.destroyedTichus)
        assertEquals(2, b.tichuCalls)
        assertEquals(30.0, a.goodHorseRatio)
        assertEquals(null, b.goodHorseRatio)
    }

    @Test
    fun `a call broken by the caller's own partner counts as a call but destroys nothing`() {
        // a: 50 base, a1 GRAND (3rd) fails because a2 finishes first: TP 50 - 200 = -150, VP 0.
        // b: 50 base, no calls, finishes 2nd and 4th: TP 50, VP 2.
        val stats = TeamStatistics.compute(teams, listOf(completedRound(
            game(1, 1, 1, side("a", 50, p("a", 1, 3, TichuCall.GRAND), p("a", 2, 1)),
                side("b", 50, p("b", 1, 2), p("b", 2, 4))),
        )), settings)

        val a = stats.single { it.teamId == TeamId("a") }
        val b = stats.single { it.teamId == TeamId("b") }
        assertEquals(1, a.tichuCalls)
        assertEquals(0, a.destroyedTichus)
        assertEquals(0, b.destroyedTichus)
        assertEquals(null, a.goodHorseRatio)
        assertEquals(25.0, b.goodHorseRatio)
    }

    @Test
    fun `a double win destroys every failing call of the losing team, grand included`() {
        // Double win a (positions 1 and 2, base 0/0): TP 0 + 200 double win bonus = 200, VP 3.
        // b (no position): its GRAND call fails: TP 0 - 200 = -200, VP 0.
        val stats = TeamStatistics.compute(teams, listOf(completedRound(
            game(1, 1, 1, side("a", 0, p("a", 1, 1), p("a", 2, 2)),
                side("b", 0, p("b", 1, null, TichuCall.GRAND), p("b", 2, null))),
        )), settings)

        val a = stats.single { it.teamId == TeamId("a") }
        val b = stats.single { it.teamId == TeamId("b") }
        assertEquals(1, a.doubleWins)
        assertEquals(1, a.destroyedTichus)
        assertEquals(0, b.destroyedTichus)
        assertEquals(200.0 / 3, a.goodHorseRatio)
        assertEquals(null, b.goodHorseRatio)
    }

    @Test
    fun `a Beschiss game counts for both of its teams and no other`() {
        val stats = TeamStatistics.compute(teams, listOf(completedRound(
            game(1, 1, 1, side("a", 60, p("a", 1, 1), p("a", 2, 3)), side("b", 40, p("b", 1, 2), p("b", 2, 4)), beschiss = true),
            game(1, 1, 2, side("a", 40, p("a", 1, 2), p("a", 2, 4)), side("b", 60, p("b", 1, 1), p("b", 2, 3))),
        )), settings)

        assertEquals(1, stats.single { it.teamId == TeamId("a") }.beschissGames)
        assertEquals(1, stats.single { it.teamId == TeamId("b") }.beschissGames)
        assertEquals(0, stats.single { it.teamId == TeamId("c") }.beschissGames)
    }

    @Test
    fun `the good horse ratio is the summed Tichu Points over the summed Victory Points`() {
        // Game 1: a 60 + small 100 = 160 TP, b 40 TP; a wins 2 VP.
        // Game 2: a 30 TP, b 70 + grand 200 = 270 TP; b wins 2 VP.
        // Game 3: tie 50 TP each; 1 VP each.
        // a: TP 160 + 30 + 50 = 240, VP 2 + 0 + 1 = 3, ratio 80.0.
        // b: TP 40 + 270 + 50 = 360, VP 0 + 2 + 1 = 3, ratio 120.0.
        val stats = TeamStatistics.compute(teams, listOf(completedRound(
            game(1, 1, 1, side("a", 60, p("a", 1, 1, TichuCall.SMALL), p("a", 2, 3)), side("b", 40, p("b", 1, 2), p("b", 2, 4))),
            game(1, 1, 2, side("a", 30, p("a", 1, 2), p("a", 2, 4)), side("b", 70, p("b", 1, 1, TichuCall.GRAND), p("b", 2, 3))),
            game(1, 1, 3, side("a", 50, p("a", 1, 1), p("a", 2, 3)), side("b", 50, p("b", 1, 2), p("b", 2, 4))),
        )), settings)

        assertEquals(80.0, stats.single { it.teamId == TeamId("a") }.goodHorseRatio)
        assertEquals(120.0, stats.single { it.teamId == TeamId("b") }.goodHorseRatio)
    }

    @Test
    fun `a negative good horse ratio is kept and a team without Victory Points has none`() {
        // a: 50 base, a1 SMALL (3rd) fails: TP 50 - 100 = -50; a2 first out; VP 2 as the higher TP.
        // b: 50 base, b2 GRAND (4th) fails: TP 50 - 200 = -150; VP 0.
        // a: ratio -50 / 2 = -25.0. b: no VP, so no ratio.
        val stats = TeamStatistics.compute(teams, listOf(completedRound(
            game(1, 1, 1, side("a", 50, p("a", 1, 3, TichuCall.SMALL), p("a", 2, 1)),
                side("b", 50, p("b", 1, 2), p("b", 2, 4, TichuCall.GRAND))),
        )), settings)

        assertEquals(-25.0, stats.single { it.teamId == TeamId("a") }.goodHorseRatio)
        assertEquals(null, stats.single { it.teamId == TeamId("b") }.goodHorseRatio)
    }

    @Test
    fun `games of tables that are not completed count for no new statistic`() {
        // Table 1 (a vs b) is completed and plain. Table 2 (c vs d) is not completed: its
        // SMALL call by c (first out) would destroy d's call, and its game is flagged Beschiss.
        val round = PlayedRound(
            round = round(1, listOf("a", "b"), listOf("c", "d")),
            games = listOf(
                game(1, 1, 1, side("a", 50, p("a", 1, 1), p("a", 2, 3)), side("b", 50, p("b", 1, 2), p("b", 2, 4))),
                game(1, 2, 1, side("c", 60, p("c", 1, 1, TichuCall.SMALL), p("c", 2, 3)),
                    side("d", 40, p("d", 1, 2, TichuCall.SMALL), p("d", 2, 4)), beschiss = true),
            ),
            confirmations = mapOf(completed(1, "a", "b"), TableId(2) to setOf(TeamId("c"))),
        )

        val stats = TeamStatistics.compute(teams, listOf(round), settings)

        for (team in listOf("c", "d")) {
            val s = stats.single { it.teamId == TeamId(team) }
            assertEquals(0, s.tichuCalls, "tichuCalls of $team")
            assertEquals(0, s.destroyedTichus, "destroyedTichus of $team")
            assertEquals(0, s.beschissGames, "beschissGames of $team")
            assertEquals(null, s.goodHorseRatio, "goodHorseRatio of $team")
        }
    }
}
