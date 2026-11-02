package tichu.domain

import tichu.vocabulary.GameValidationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Unit tests for the [Game] aggregate's scoring seam: [Game.record] builds a game
 * from submitted team facts, [Game.score] derives the outcome from the stored
 * facts. The scoring rules themselves are covered by [GameScoringTest].
 */
class GameTest {

    private val settings = TournamentSettings()
    private val key = GameKey(TournamentId("t-1"), roundNumber = 1, tableNumber = 2, gameNumber = 3)

    private val teamA = TeamResult(
        TeamId("team-a"),
        baseScore = 60,
        players = listOf(
            PlayerResult(PlayerId("a1"), 1, TichuCall.SMALL, 0),
            PlayerResult(PlayerId("a2"), 3, TichuCall.NONE, 1),
        ),
    )
    private val teamB = TeamResult(
        TeamId("team-b"),
        baseScore = 40,
        players = listOf(
            PlayerResult(PlayerId("b1"), 2, TichuCall.NONE, 0),
            PlayerResult(PlayerId("b2"), 4, TichuCall.NONE, 2),
        ),
    )

    @Test
    fun `record round-trips the submitted team facts`() {
        val game = Game.record(key, listOf(teamA, teamB), beschiss = true, settings = settings)

        assertEquals(key, game.key)
        assertEquals(listOf(teamA, teamB), game.teamResults)
        assertEquals(true, game.beschiss)
        assertNull(game.id, "the adapter assigns the surrogate id")
    }

    @Test
    fun `score derives the outcome from the stored facts`() {
        val outcome = Game.record(key, listOf(teamA, teamB), beschiss = false, settings = settings).score(settings)

        assertEquals(160, outcome.teamPoints[TeamId("team-a")])
        assertEquals(40, outcome.teamPoints[TeamId("team-b")])
        assertEquals(setOf(PlayerId("a1")), outcome.tichuSuccesses)
    }

    @Test
    fun `record rejects invalid facts with a field-anchored validation error`() {
        val incomplete = teamB.copy(players = teamB.players.take(1))

        val error = assertFailsWith<GameValidationException> {
            Game.record(key, listOf(teamA, incomplete), beschiss = false, settings = settings)
        }
        assertEquals("players", error.field)
    }
}
