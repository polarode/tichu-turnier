package tichu.domain

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for the statistics Highlights (GLOSSARY.md: **Highlight**): per metric,
 * the team leading it with every tied team shown; a metric whose maximum is 0 has no
 * highlight.
 *
 * Hand-worked fixture (values are literal, not derived from the code under test):
 * - Successful Tichu (small + grand): a 2 + 1 = 3, b 1 + 0 = 1, c 3 + 0 = 3, d 0, e 0.
 *   Maximum 3, tied by a and c.
 * - Bombs: a 4, b 4, c 0, d 1, e 0. Maximum 4, tied by a and b.
 * - Double wins: a 0, b 2, c 2, d 0, e 0. Maximum 2, tied by b and c.
 */
class HighlightTest {

    private fun stats(
        team: String,
        small: Int = 0,
        grand: Int = 0,
        bombs: Int = 0,
        doubleWins: Int = 0,
    ) = TeamStatistics(
        teamId = TeamId(team),
        successfulSmallTichu = small,
        successfulGrandTichu = grand,
        bombCount = bombs,
        bombsPerGame = null,
        doubleWins = doubleWins,
    )

    @Test
    fun `each metric highlights every team tied for its maximum, in input order`() {
        val highlights = Highlight.of(
            listOf(
                stats("a", small = 2, grand = 1, bombs = 4, doubleWins = 0),
                stats("b", small = 1, grand = 0, bombs = 4, doubleWins = 2),
                stats("c", small = 3, grand = 0, bombs = 0, doubleWins = 2),
                stats("d", small = 0, grand = 0, bombs = 1, doubleWins = 0),
                stats("e", small = 0, grand = 0, bombs = 0, doubleWins = 0),
            ),
        )

        assertEquals(
            listOf(
                Highlight(HighlightMetric.SUCCESSFUL_TICHU, 3, listOf(TeamId("a"), TeamId("c"))),
                Highlight(HighlightMetric.BOMBS, 4, listOf(TeamId("a"), TeamId("b"))),
                Highlight(HighlightMetric.DOUBLE_WINS, 2, listOf(TeamId("b"), TeamId("c"))),
            ),
            highlights,
        )
    }

    @Test
    fun `a metric whose maximum is zero has no highlight`() {
        val highlights = Highlight.of(
            listOf(
                stats("a", small = 1, bombs = 0, doubleWins = 0),
                stats("b", small = 0, bombs = 0, doubleWins = 0),
            ),
        )

        assertEquals(
            listOf(Highlight(HighlightMetric.SUCCESSFUL_TICHU, 1, listOf(TeamId("a")))),
            highlights,
        )
    }
}
