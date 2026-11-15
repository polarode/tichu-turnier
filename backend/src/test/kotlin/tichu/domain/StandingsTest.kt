package tichu.domain

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for the pure standings component (GLOSSARY.md: **Standings**):
 * VP and TP sums over completed tables only, ranked VP desc, TP desc, with
 * shared ranks on exact ties and name order within them.
 */
class StandingsTest {

    private val tournamentId = TournamentId("t-1")
    private val settings = TournamentSettings()

    private fun team(id: String, name: String = id.uppercase()) = Team(
        id = TeamId(id),
        tournamentId = tournamentId,
        name = name,
        players = listOf(
            Player(PlayerId("$id-1"), tournamentId, TeamId(id), "$id one"),
            Player(PlayerId("$id-2"), tournamentId, TeamId(id), "$id two"),
        ),
    )

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
        val teams = listOf(team("a"), team("b"))
        val played = PlayedRound(
            round = round(1, listOf("a", "b")),
            games = normalTable(1, 1, "a", "b", 60, 70, 50, 20),
            confirmations = mapOf(completed(1, "a", "b")),
        )

        val standings = Standings.compute(teams, listOf(played), settings)

        // a: wins 60, 70 (4 VP), ties 50 (1 VP), loses 20 → 5 VP, 200 TP; b: 1 + 2 = 3 VP, 200 TP.
        assertEquals(
            listOf(
                StandingsEntry(1, TeamTotals(TeamId("a"), "A", gamesPlayed = 4, victoryPoints = 5, tichuPoints = 200)),
                StandingsEntry(2, TeamTotals(TeamId("b"), "B", gamesPlayed = 4, victoryPoints = 3, tichuPoints = 200)),
            ),
            standings.entries,
        )
    }

    @Test
    fun `only completed tables count, trio tables included, summed across rounds`() {
        val teams = listOf(team("a"), team("b"), team("c"), team("d"), team("e"))
        // Round 1: table 1 (a-b) completed; table 2 is the trio c-d-e, completed.
        // Trio schedule: 1 CD, 2 CE, 3 DE, 4 CD, 5 CE, 6 DE (seat order c, d, e).
        val round1 = PlayedRound(
            round = round(1, listOf("a", "b"), listOf("c", "d", "e")),
            games = normalTable(1, 1, "a", "b", 60, 60, 60, 60) + listOf(
                game(1, 2, 1, "c", "d", 70),
                game(1, 2, 2, "c", "e", 70),
                game(1, 2, 3, "d", "e", 70),
                game(1, 2, 4, "c", "d", 70),
                game(1, 2, 5, "c", "e", 70),
                game(1, 2, 6, "d", "e", 70),
            ),
            confirmations = mapOf(completed(1, "a", "b"), completed(2, "c", "d", "e")),
        )
        // Round 2: a-c entered in full but only a confirmed (Confirming); b-d still playing; e's trio absent.
        val round2 = PlayedRound(
            round = round(2, listOf("a", "c"), listOf("b", "d")),
            games = normalTable(2, 1, "a", "c", 90, 90, 90, 90) + normalTable(2, 2, "b", "d", 90, 90),
            confirmations = mapOf(TableId(1) to setOf(TeamId("a"))),
        )

        val standings = Standings.compute(teams, listOf(round1, round2), settings)

        // Round 1 only: a 4 wins (8 VP, 240 TP); b 0 VP, 160 TP; trio: c wins all four (8 VP, 280 TP),
        // d wins 2 of 4 (4 VP, 2×30 + 2×70 = 200 TP), e loses all four (0 VP, 120 TP).
        assertEquals(
            listOf(
                StandingsEntry(1, TeamTotals(TeamId("c"), "C", 4, 8, 280)),
                StandingsEntry(2, TeamTotals(TeamId("a"), "A", 4, 8, 240)),
                StandingsEntry(3, TeamTotals(TeamId("d"), "D", 4, 4, 200)),
                StandingsEntry(4, TeamTotals(TeamId("b"), "B", 4, 0, 160)),
                StandingsEntry(5, TeamTotals(TeamId("e"), "E", 4, 0, 120)),
            ),
            standings.entries,
        )
    }

    @Test
    fun `teams without a completed game are listed with zeros, sharing the bottom rank`() {
        val teams = listOf(team("a"), team("b"), team("c"), team("d"))
        val played = PlayedRound(
            round = round(1, listOf("a", "b"), listOf("c", "d")),
            games = normalTable(1, 1, "a", "b", 60, 60, 60, 60),
            confirmations = mapOf(completed(1, "a", "b")),
        )

        val standings = Standings.compute(teams, listOf(played), settings)

        assertEquals(
            listOf(1 to "A", 2 to "B", 3 to "C", 3 to "D"),
            standings.entries.map { it.rank to it.team.teamName },
        )
        assertEquals(listOf(4, 4, 0, 0), standings.entries.map { it.team.gamesPlayed })
    }

    private fun totals(id: String, name: String, victoryPoints: Int, tichuPoints: Int) =
        TeamTotals(TeamId(id), name, gamesPlayed = 4, victoryPoints = victoryPoints, tichuPoints = tichuPoints)

    @Test
    fun `exact ties share a rank, the next rank skips, and tied teams are listed by name`() {
        val standings = Standings.rank(
            listOf(
                totals("x", "owls", victoryPoints = 4, tichuPoints = 150),
                totals("y", "Falcons", victoryPoints = 6, tichuPoints = 100),
                totals("z", "Ravens", victoryPoints = 4, tichuPoints = 150),
                totals("w", "Badgers", victoryPoints = 2, tichuPoints = 300),
            ),
        )

        // Name order ignores case: "owls" before "Ravens".
        assertEquals(
            listOf(1 to "Falcons", 2 to "owls", 2 to "Ravens", 4 to "Badgers"),
            standings.entries.map { it.rank to it.team.teamName },
        )
    }

    @Test
    fun `the order does not depend on the order of the input`() {
        val totals = listOf(
            totals("a", "Alpha", victoryPoints = 4, tichuPoints = 150),
            totals("b", "Beta", victoryPoints = 4, tichuPoints = 150),
            totals("c", "Gamma", victoryPoints = 6, tichuPoints = 90),
            totals("d", "Delta", victoryPoints = 4, tichuPoints = 160),
        )

        val expected = Standings.rank(totals)

        for (seed in 0 until 10) {
            assertEquals(expected, Standings.rank(totals.shuffled(kotlin.random.Random(seed))))
        }
        assertEquals(listOf("Gamma", "Delta", "Alpha", "Beta"), expected.entries.map { it.team.teamName })
    }

    @Test
    fun `equal victory points are ordered by tichu points without sharing a rank`() {
        val standings = Standings.rank(
            listOf(
                totals("a", "Alpha", victoryPoints = 4, tichuPoints = 90),
                totals("b", "Beta", victoryPoints = 4, tichuPoints = 110),
            ),
        )

        assertEquals(listOf(1 to "Beta", 2 to "Alpha"), standings.entries.map { it.rank to it.team.teamName })
    }

    private fun withdrawal(reason: WithdrawalReason, compensation: ForfeitCompensation) = Withdrawal(
        reason = reason,
        compensation = compensation,
        roundNumber = 1,
        withdrawnAt = Instant.EPOCH,
        withdrawnBy = AdminUserId("admin-1"),
    )

    /**
     * Team b withdraws after two of its four games at table 1 (a wins them with 60 and 70)
     * and a confirms, so the table completes. The two games b still owed are forfeits to a.
     */
    private fun standingsAfterVoluntaryWithdrawal(compensation: ForfeitCompensation): Standings {
        val withdrawn = withdrawal(WithdrawalReason.VOLUNTARY, compensation)
        val played = PlayedRound(
            round = round(1, listOf("a", "b")),
            games = normalTable(1, 1, "a", "b", 60, 70),
            confirmations = mapOf(completed(1, "a")),
            withdrawals = mapOf(TeamId("b") to withdrawn),
        )
        return Standings.compute(listOf(team("a"), team("b").copy(withdrawal = withdrawn)), listOf(played), settings)
    }

    @Test
    fun `a voluntary withdrawal keeps the played games and awards the owed ones at full compensation`() {
        // a: 4 VP and 130 TP from the two played wins, plus 2 forfeits at 2 VP and 100 TP each.
        // b: 0 VP and 70 TP from its two played games; it keeps its rank, marked as voluntary.
        assertEquals(
            listOf(
                StandingsEntry(1, TeamTotals(TeamId("a"), "A", gamesPlayed = 2, victoryPoints = 8, tichuPoints = 330)),
                StandingsEntry(
                    2,
                    TeamTotals(
                        TeamId("b"),
                        "B",
                        gamesPlayed = 2,
                        victoryPoints = 0,
                        tichuPoints = 70,
                        withdrawalReason = WithdrawalReason.VOLUNTARY,
                    ),
                ),
            ),
            standingsAfterVoluntaryWithdrawal(ForfeitCompensation.FULL).entries,
        )
    }

    @Test
    fun `a voluntary withdrawal awards the owed games at half compensation`() {
        // a: 4 VP and 130 TP played, plus 2 forfeits at 1 VP and 50 TP each.
        assertEquals(
            TeamTotals(TeamId("a"), "A", gamesPlayed = 2, victoryPoints = 6, tichuPoints = 230),
            standingsAfterVoluntaryWithdrawal(ForfeitCompensation.HALF).entries.single { it.team.teamId == TeamId("a") }.team,
        )
    }

    @Test
    fun `a voluntary withdrawal awards nothing for the owed games at no compensation`() {
        // a: 4 VP and 130 TP from the played wins only; the forfeits give 0 VP and 0 TP.
        assertEquals(
            TeamTotals(TeamId("a"), "A", gamesPlayed = 2, victoryPoints = 4, tichuPoints = 130),
            standingsAfterVoluntaryWithdrawal(ForfeitCompensation.NONE).entries.single { it.team.teamId == TeamId("a") }.team,
        )
    }

    @Test
    fun `disqualified teams are listed last without a rank, ordered among themselves by victory points`() {
        val standings = Standings.rank(
            listOf(
                totals("x", "Xeno", victoryPoints = 10, tichuPoints = 500).copy(withdrawalReason = WithdrawalReason.DISQUALIFIED),
                totals("a", "Alpha", victoryPoints = 4, tichuPoints = 100),
                totals("y", "Yak", victoryPoints = 6, tichuPoints = 300).copy(withdrawalReason = WithdrawalReason.DISQUALIFIED),
                totals("b", "Beta", victoryPoints = 2, tichuPoints = 90),
                totals("c", "Gamma", victoryPoints = 2, tichuPoints = 50),
            ),
        )

        assertEquals(
            listOf(1 to "Alpha", 2 to "Beta", 3 to "Gamma", null to "Xeno", null to "Yak"),
            standings.entries.map { it.rank to it.team.teamName },
        )
    }
}
