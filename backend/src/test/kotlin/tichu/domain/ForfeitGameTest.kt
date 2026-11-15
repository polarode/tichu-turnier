package tichu.domain

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for the Forfeit Games of a withdrawn team (GLOSSARY.md: **Forfeit Game**,
 * ADR 0011): derived from the games still owed at the withdrawn team's table, scored
 * as VP and TP only, and counted only once the table completed.
 */
class ForfeitGameTest {

    private val tournamentId = TournamentId("t-1")
    private val settings = TournamentSettings()

    private val teamA = TeamId("a")
    private val teamB = TeamId("b")
    private val teamC = TeamId("c")

    private fun round(number: Int, vararg tables: List<TeamId>) = Round(
        id = RoundId("r-$number"),
        tournamentId = tournamentId,
        number = number,
        pairing = Pairing(tables.mapIndexed { i, seated -> Table(TableId(i + 1), seated) }),
    )

    private fun withdrawal(
        roundNumber: Int,
        compensation: ForfeitCompensation = ForfeitCompensation.FULL,
    ) = Withdrawal(
        reason = WithdrawalReason.VOLUNTARY,
        compensation = compensation,
        roundNumber = roundNumber,
        withdrawnAt = Instant.parse("2026-11-15T10:00:00Z"),
        withdrawnBy = AdminUserId("admin-1"),
    )

    /** A plain game: [first] takes [firstBase] of the 100 base points, no calls, no bombs. */
    private fun game(roundNumber: Int, tableNumber: Int, gameNumber: Int, first: TeamId, second: TeamId, firstBase: Int): Game {
        val firstWins = firstBase >= 50
        return Game.record(
            GameKey(tournamentId, roundNumber, tableNumber, gameNumber),
            listOf(
                TeamResult(first, firstBase, listOf(
                    PlayerResult(PlayerId("${first.value}-1"), if (firstWins) 1 else 2, TichuCall.NONE, 0),
                    PlayerResult(PlayerId("${first.value}-2"), if (firstWins) 3 else 4, TichuCall.NONE, 0),
                )),
                TeamResult(second, 100 - firstBase, listOf(
                    PlayerResult(PlayerId("${second.value}-1"), if (firstWins) 2 else 1, TichuCall.NONE, 0),
                    PlayerResult(PlayerId("${second.value}-2"), if (firstWins) 4 else 3, TichuCall.NONE, 0),
                )),
            ),
            beschiss = false,
            settings = settings,
        )
    }

    @Test
    fun `a withdrawn team's unplayed games at a normal table forfeit to the remaining team`() {
        val played = PlayedRound(
            round = round(1, listOf(teamA, teamB)),
            games = listOf(game(1, 1, 1, teamA, teamB, 60), game(1, 1, 2, teamA, teamB, 70)),
            confirmations = emptyMap(),
            withdrawals = mapOf(teamB to withdrawal(1)),
        )

        assertEquals(
            listOf(
                ForfeitGame(1, 1, 3, withdrawnTeam = teamB, opponent = teamA, awarded = true, compensation = ForfeitCompensation.FULL),
                ForfeitGame(1, 1, 4, withdrawnTeam = teamB, opponent = teamA, awarded = true, compensation = ForfeitCompensation.FULL),
            ),
            played.forfeits,
        )
    }

    @Test
    fun `a withdrawal from another round forfeits nothing`() {
        val played = PlayedRound(
            round = round(1, listOf(teamA, teamB)),
            games = listOf(game(1, 1, 1, teamA, teamB, 60)),
            confirmations = emptyMap(),
            withdrawals = mapOf(teamB to withdrawal(2)),
        )

        assertEquals(emptyList(), played.forfeits)
    }

    @Test
    fun `at a trio table only the withdrawn team's unplayed games forfeit, the other two still play`() {
        val played = PlayedRound(
            round = round(1, listOf(teamA, teamB, teamC)),
            games = listOf(game(1, 1, 1, teamA, teamB, 60), game(1, 1, 2, teamA, teamC, 60)),
            confirmations = emptyMap(),
            withdrawals = mapOf(teamC to withdrawal(1)),
        )

        assertEquals(
            listOf(
                ForfeitGame(1, 1, 3, withdrawnTeam = teamC, opponent = teamB, awarded = true, compensation = ForfeitCompensation.FULL),
                ForfeitGame(1, 1, 5, withdrawnTeam = teamC, opponent = teamA, awarded = true, compensation = ForfeitCompensation.FULL),
                ForfeitGame(1, 1, 6, withdrawnTeam = teamC, opponent = teamB, awarded = true, compensation = ForfeitCompensation.FULL),
            ),
            played.forfeits,
        )
    }

    @Test
    fun `a forfeit game of a team that withdrew with nobody left to award gives both teams nothing`() {
        val forfeit = ForfeitGame(1, 1, 3, withdrawnTeam = teamB, opponent = teamA, awarded = false, compensation = ForfeitCompensation.FULL)

        assertEquals(0, forfeit.outcome().victoryPoints.getValue(teamA))
        assertEquals(0, forfeit.outcome().teamPoints.getValue(teamA))
        assertEquals(0, forfeit.outcome().victoryPoints.getValue(teamB))
        assertEquals(0, forfeit.outcome().teamPoints.getValue(teamB))
    }

    @Test
    fun `a full forfeit awards the remaining team 2 VP and 100 TP`() {
        val forfeit = ForfeitGame(1, 1, 3, withdrawnTeam = teamB, opponent = teamA, awarded = true, compensation = ForfeitCompensation.FULL)

        assertEquals(
            GameOutcome(tichuSuccesses = emptySet(), doubleWinTeam = null, teamPoints = mapOf(teamA to 100, teamB to 0), victoryPoints = mapOf(teamA to 2, teamB to 0)),
            forfeit.outcome(),
        )
    }

    @Test
    fun `a half forfeit awards the remaining team 1 VP and 50 TP`() {
        val forfeit = ForfeitGame(1, 1, 3, withdrawnTeam = teamB, opponent = teamA, awarded = true, compensation = ForfeitCompensation.HALF)

        assertEquals(
            GameOutcome(tichuSuccesses = emptySet(), doubleWinTeam = null, teamPoints = mapOf(teamA to 50, teamB to 0), victoryPoints = mapOf(teamA to 1, teamB to 0)),
            forfeit.outcome(),
        )
    }

    @Test
    fun `a forfeit without compensation awards the remaining team 0 VP and 0 TP`() {
        val forfeit = ForfeitGame(1, 1, 3, withdrawnTeam = teamB, opponent = teamA, awarded = true, compensation = ForfeitCompensation.NONE)

        assertEquals(
            GameOutcome(tichuSuccesses = emptySet(), doubleWinTeam = null, teamPoints = mapOf(teamA to 0, teamB to 0), victoryPoints = mapOf(teamA to 0, teamB to 0)),
            forfeit.outcome(),
        )
    }

    @Test
    fun `forfeits count only once the table completed, which needs the remaining team to confirm`() {
        val unconfirmed = PlayedRound(
            round = round(1, listOf(teamA, teamB)),
            games = emptyList(),
            confirmations = emptyMap(),
            withdrawals = mapOf(teamB to withdrawal(1)),
        )
        val confirmed = unconfirmed.copy(confirmations = mapOf(TableId(1) to setOf(teamA)))

        assertTrue(unconfirmed.completedForfeits.isEmpty())
        assertEquals(confirmed.forfeits, confirmed.completedForfeits)
        assertEquals(4, confirmed.completedForfeits.size)
    }
}
