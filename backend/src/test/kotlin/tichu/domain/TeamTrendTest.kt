package tichu.domain

import tichu.testsupport.teamOf
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for the Team Trend (GLOSSARY.md: **Team Trend**): a team's cumulative
 * Victory Points after each completed round it was seated in, over the games of
 * completed tables only.
 *
 * Hand-worked scenario (plain games: a win is 2 VP, a loss 0 VP, no calls, no bombs):
 * - Round 1 (completed): table 1 a vs b with a's bases 60, 70, 50, 20 gives a 2+2+1+0 = 5 VP
 *   and b 3 VP; table 2 c vs d with c's bases 30 x4 gives c 0 VP and d 8 VP.
 * - Round 2 (completed): table 1 a vs c with a's bases 90, 90, 40, 40 gives a 4 VP and c 4 VP;
 *   table 2 b vs d with bases 50 x4 gives b 4 VP and d 4 VP (1 VP per tie).
 * - Round 3 (not completed): table 1 a vs b has games entered but no confirmations, and
 *   table 2 c vs d has nothing; the round must produce no point.
 * - Team e is seated in no round, so its trend is empty.
 * Cumulative trends: a 5, 9; b 3, 7; c 0, 4; d 8, 12.
 */
class TeamTrendTest {

    private val tournamentId = TournamentId("t-1")
    private val settings = TournamentSettings()

    private val teams = listOf("a", "b", "c", "d", "e").map { teamOf(tournamentId, it) }

    private fun round(number: Int, vararg tables: List<String>) = Round(
        id = RoundId("r-$number"),
        tournamentId = tournamentId,
        number = number,
        pairing = Pairing(tables.mapIndexed { i, seated -> Table(TableId(i + 1), seated.map(::TeamId)) }),
    )

    private fun completed(tableNumber: Int, vararg teams: String) = TableId(tableNumber) to teams.map(::TeamId).toSet()

    /** A plain game: [first] takes [firstBase] of the 100 base points; the winner has positions 1 and 3. */
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

    private fun table(roundNumber: Int, tableNumber: Int, first: String, second: String, vararg firstBases: Int) =
        firstBases.mapIndexed { i, base -> game(roundNumber, tableNumber, i + 1, first, second, base) }

    private val rounds = listOf(
        PlayedRound(
            round = round(1, listOf("a", "b"), listOf("c", "d")),
            games = table(1, 1, "a", "b", 60, 70, 50, 20) + table(1, 2, "c", "d", 30, 30, 30, 30),
            confirmations = mapOf(completed(1, "a", "b"), completed(2, "c", "d")),
        ),
        PlayedRound(
            round = round(2, listOf("a", "c"), listOf("b", "d")),
            games = table(2, 1, "a", "c", 90, 90, 40, 40) + table(2, 2, "b", "d", 50, 50, 50, 50),
            confirmations = mapOf(completed(1, "a", "c"), completed(2, "b", "d")),
        ),
        PlayedRound(
            round = round(3, listOf("a", "b"), listOf("c", "d")),
            games = table(3, 1, "a", "b", 80, 80, 80, 80),
            confirmations = emptyMap(),
        ),
    )

    @Test
    fun `each team's trend is its cumulative victory points after each completed round it was seated in`() {
        val trends = TeamTrend.compute(teams, rounds, settings)

        assertEquals(
            listOf(
                TeamTrend(TeamId("a"), listOf(TrendPoint(1, 5), TrendPoint(2, 9))),
                TeamTrend(TeamId("b"), listOf(TrendPoint(1, 3), TrendPoint(2, 7))),
                TeamTrend(TeamId("c"), listOf(TrendPoint(1, 0), TrendPoint(2, 4))),
                TeamTrend(TeamId("d"), listOf(TrendPoint(1, 8), TrendPoint(2, 12))),
                TeamTrend(TeamId("e"), emptyList()),
            ),
            trends,
        )
    }

    @Test
    fun `the last point of each team's trend equals its victory points in the standings`() {
        val trends = TeamTrend.compute(teams, rounds, settings)
        val standings = Standings.compute(teams, rounds, settings)

        for (entry in standings.entries) {
            val trend = trends.single { it.teamId == entry.team.teamId }
            if (trend.points.isEmpty()) {
                assertEquals(0, entry.team.victoryPoints, "${entry.team.teamName} has no points but standings VP")
            } else {
                assertEquals(entry.team.victoryPoints, trend.points.last().victoryPoints, entry.team.teamName)
            }
        }
    }
}
