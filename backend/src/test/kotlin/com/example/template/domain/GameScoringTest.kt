package com.example.template.domain

import com.example.template.vocabulary.GameValidationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Unit tests for the pure game scoring component (issue #99, S5a). All tests work
 * at the public seam [GameScoring.score]; no Spring, no persistence.
 */
class GameScoringTest {

    private val settings = TournamentSettings()

    private val players = listOf("a", "b", "c", "d").map { PlayerId(it) }
    private var nextPlayer = 0

    private fun playerResult(position: Int?, call: TichuCall = TichuCall.NONE, bombs: Int = 0) =
        PlayerResult(players[nextPlayer++], position, call, bombs)

    @Test
    fun `plain game derives bonuses, total TP and VP from raw facts`() {
        val outcome = GameScoring.score(
            teams = listOf(
                TeamResult(TeamId("t1"), baseScore = 60, players = listOf(playerResult(1, TichuCall.SMALL), playerResult(3))),
                TeamResult(TeamId("t2"), baseScore = 40, players = listOf(playerResult(2), playerResult(4))),
            ),
            settings = settings,
        )

        assertEquals(160, outcome.teamPoints[TeamId("t1")])
        assertEquals(40, outcome.teamPoints[TeamId("t2")])
        assertEquals(2, outcome.victoryPoints[TeamId("t1")])
        assertEquals(0, outcome.victoryPoints[TeamId("t2")])
        assertEquals(null, outcome.doubleWinTeam)
    }

    @Test
    fun `successful grand tichu yields plus 200, failed yields minus 200`() {
        val outcome = GameScoring.score(
            teams = listOf(
                TeamResult(TeamId("t1"), 30, listOf(playerResult(1, TichuCall.GRAND), playerResult(3))),
                TeamResult(TeamId("t2"), 70, listOf(playerResult(2), playerResult(4, TichuCall.GRAND))),
            ),
            settings = settings,
        )

        assertEquals(230, outcome.teamPoints[TeamId("t1")])
        assertEquals(-130, outcome.teamPoints[TeamId("t2")])
        assertEquals(2, outcome.victoryPoints[TeamId("t1")])
        assertEquals(0, outcome.victoryPoints[TeamId("t2")])
    }

    @Test
    fun `failed small tichu yields minus 100`() {
        val outcome = GameScoring.score(
            teams = listOf(
                TeamResult(TeamId("t1"), 80, listOf(playerResult(2, TichuCall.SMALL), playerResult(4))),
                TeamResult(TeamId("t2"), 20, listOf(playerResult(1), playerResult(3))),
            ),
            settings = settings,
        )

        assertEquals(-20, outcome.teamPoints[TeamId("t1")])
        assertEquals(20, outcome.teamPoints[TeamId("t2")])
    }

    @Test
    fun `tie on total TP awards one victory point to each team`() {
        // A symmetric tie: both teams 50 base, no bonuses. Ties need not be 50-50
        // base in general — bonuses can equalize — but the plain case pins the VP rule.
        val outcome = GameScoring.score(
            teams = listOf(
                TeamResult(TeamId("t1"), 50, listOf(playerResult(1), playerResult(3))),
                TeamResult(TeamId("t2"), 50, listOf(playerResult(2), playerResult(4))),
            ),
            settings = settings,
        )

        assertEquals(1, outcome.victoryPoints[TeamId("t1")])
        assertEquals(1, outcome.victoryPoints[TeamId("t2")])
    }

    @Test
    fun `double win forces base 0 0, sets 200 bonus and 3 VP, losers have no position`() {
        val outcome = GameScoring.score(
            teams = listOf(
                TeamResult(TeamId("t1"), 0, listOf(playerResult(1, bombs = 1), playerResult(2))),
                TeamResult(TeamId("t2"), 0, listOf(playerResult(null, bombs = 2), playerResult(null, bombs = 3))),
            ),
            settings = settings,
        )

        assertEquals(TeamId("t1"), outcome.doubleWinTeam)
        assertEquals(200, outcome.teamPoints[TeamId("t1")])
        assertEquals(0, outcome.teamPoints[TeamId("t2")])
        assertEquals(3, outcome.victoryPoints[TeamId("t1")])
        assertEquals(0, outcome.victoryPoints[TeamId("t2")])
        assertEquals(emptySet(), outcome.tichuSuccesses)
    }

    @Test
    fun `multiple players may call tichu in the same game`() {
        val outcome = GameScoring.score(
            teams = listOf(
                TeamResult(TeamId("t1"), 40, listOf(playerResult(1, TichuCall.SMALL), playerResult(3))),
                TeamResult(TeamId("t2"), 60, listOf(playerResult(2, TichuCall.GRAND), playerResult(4))),
            ),
            settings = settings,
        )

        // t2's grand call failed (position 2): -200. t1's small succeeded: +100.
        assertEquals(140, outcome.teamPoints[TeamId("t1")])
        assertEquals(-140, outcome.teamPoints[TeamId("t2")])
        assertEquals(setOf(players[0]), outcome.tichuSuccesses)
    }

    // --- validation: every rejection below ports a case from the v1 matrix ---

    @Test
    fun `rejects wrong number of participants`() {
        assertFailsWith<GameValidationException> {
            GameScoring.score(
                teams = listOf(
                    TeamResult(TeamId("t1"), 50, listOf(playerResult(1), playerResult(3))),
                    TeamResult(TeamId("t2"), 50, listOf(playerResult(2))),
                ),
                settings = settings,
            )
        }
    }

    @Test
    fun `rejects duplicate positions`() {
        assertFailsWith<GameValidationException> {
            GameScoring.score(
                teams = listOf(
                    TeamResult(TeamId("t1"), 50, listOf(playerResult(1), playerResult(1))),
                    TeamResult(TeamId("t2"), 50, listOf(playerResult(3), playerResult(4))),
                ),
                settings = settings,
            )
        }
    }

    @Test
    fun `rejects the same player on both teams`() {
        val shared = PlayerId("shared")
        assertFailsWith<GameValidationException> {
            GameScoring.score(
                teams = listOf(
                    TeamResult(TeamId("t1"), 50, listOf(PlayerResult(shared, 1, TichuCall.NONE, 0), playerResult(3))),
                    TeamResult(TeamId("t2"), 50, listOf(PlayerResult(shared, 2, TichuCall.NONE, 0), playerResult(4))),
                ),
                settings = settings,
            )
        }
    }

    @Test
    fun `rejects base scores not summing to 100`() {
        assertFailsWith<GameValidationException> {
            GameScoring.score(
                teams = listOf(
                    TeamResult(TeamId("t1"), 60, listOf(playerResult(1), playerResult(3))),
                    TeamResult(TeamId("t2"), 50, listOf(playerResult(2), playerResult(4))),
                ),
                settings = settings,
            )
        }
    }

    @Test
    fun `rejects double win with non-zero base scores`() {
        assertFailsWith<GameValidationException> {
            GameScoring.score(
                teams = listOf(
                    TeamResult(TeamId("t1"), 50, listOf(playerResult(1), playerResult(2))),
                    TeamResult(TeamId("t2"), 50, listOf(playerResult(null), playerResult(null))),
                ),
                settings = settings,
            )
        }
    }

    @Test
    fun `rejects partial double win where a loser still has a position`() {
        assertFailsWith<GameValidationException> {
            GameScoring.score(
                teams = listOf(
                    TeamResult(TeamId("t1"), 50, listOf(playerResult(1), playerResult(2))),
                    TeamResult(TeamId("t2"), 50, listOf(playerResult(3), playerResult(4))),
                ),
                settings = settings,
            )
        }
    }

    @Test
    fun `rejects a position gap outside a double win`() {
        assertFailsWith<GameValidationException> {
            GameScoring.score(
                teams = listOf(
                    TeamResult(TeamId("t1"), 50, listOf(playerResult(1), playerResult(3))),
                    TeamResult(TeamId("t2"), 50, listOf(playerResult(3), playerResult(4))),
                ),
                settings = settings,
            )
        }
    }

    @Test
    fun `rejects grand tichu when the setting disables it`() {
        assertFailsWith<GameValidationException> {
            GameScoring.score(
                teams = listOf(
                    TeamResult(TeamId("t1"), 50, listOf(playerResult(1, TichuCall.GRAND), playerResult(3))),
                    TeamResult(TeamId("t2"), 50, listOf(playerResult(2), playerResult(4))),
                ),
                settings = TournamentSettings(allowGrandTichu = false),
            )
        }
    }

    @Test
    fun `rejects bomb count outside 0 to 3`() {
        assertFailsWith<GameValidationException> {
            GameScoring.score(
                teams = listOf(
                    TeamResult(TeamId("t1"), 50, listOf(playerResult(1, bombs = 5), playerResult(3))),
                    TeamResult(TeamId("t2"), 50, listOf(playerResult(2), playerResult(4))),
                ),
                settings = settings,
            )
        }
    }

    @Test
    fun `rejects a grand tichu call when the setting disables it, multiple players alike`() {
        // v1's "no simultaneous small+grand Tichu" rule is unrepresentable by
        // construction: TichuCall is a single enum per player, so a dual call
        // cannot be built. Multiple players may each call grand in one game; the
        // setting governs them all.
        assertFailsWith<GameValidationException> {
            GameScoring.score(
                teams = listOf(
                    TeamResult(TeamId("t1"), 50, listOf(playerResult(1, TichuCall.GRAND), playerResult(3, TichuCall.GRAND))),
                    TeamResult(TeamId("t2"), 50, listOf(playerResult(2), playerResult(4))),
                ),
                settings = TournamentSettings(allowGrandTichu = false),
            )
        }
    }

    @Test
    fun `successful grand tichu by a double-win winner stacks both bonuses`() {
        val outcome = GameScoring.score(
            teams = listOf(
                TeamResult(TeamId("t1"), 0, listOf(playerResult(1, TichuCall.GRAND), playerResult(2))),
                TeamResult(TeamId("t2"), 0, listOf(playerResult(null), playerResult(null))),
            ),
            settings = settings,
        )

        // 0 base + 200 grand + 200 double win.
        assertEquals(400, outcome.teamPoints[TeamId("t1")])
        assertEquals(0, outcome.teamPoints[TeamId("t2")])
    }

    @Test
    fun `failed grand tichu by a double-win loser is still penalized`() {
        val outcome = GameScoring.score(
            teams = listOf(
                TeamResult(TeamId("t1"), 0, listOf(playerResult(1), playerResult(2))),
                TeamResult(TeamId("t2"), 0, listOf(playerResult(null, TichuCall.GRAND), playerResult(null))),
            ),
            settings = settings,
        )

        // The loser never finished, so the call failed: 0 base - 200 penalty.
        assertEquals(200, outcome.teamPoints[TeamId("t1")])
        assertEquals(-200, outcome.teamPoints[TeamId("t2")])
    }
}
