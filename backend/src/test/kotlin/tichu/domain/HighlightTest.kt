package tichu.domain

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for the statistics Highlights (GLOSSARY.md: **Highlight**): per metric,
 * the team leading it with every tied team shown. Leading means the highest value,
 * except for the Good Horse Ratio, where it means the lowest non-null ratio. A count
 * metric whose maximum is 0 has no highlight; the Good Horse has none only when no team
 * has a ratio.
 *
 * Hand-worked fixture (values are literal, not derived from the code under test):
 * - Successful Tichu (small + grand): a 2 + 1 = 3, b 1 + 0 = 1, c 3 + 0 = 3, d 0, e 0.
 *   Maximum 3, tied by a and c.
 * - Bombs: a 4, b 4, c 0, d 1, e 0. Maximum 4, tied by a and b.
 * - Double wins: a 0, b 2, c 2, d 0, e 0. Maximum 2, tied by b and c.
 * - Tichu calls: a 5, b 2, c 5, d 0, e 0. Maximum 5, tied by a and c.
 * - Destroyed Tichus: a 0, b 0, c 1, d 0, e 1. Maximum 1, tied by c and e.
 * - Beschiss games: all 0, so no highlight.
 * - Good Horse ratio: a 1.2, b -0.5, c -0.5, d 0.0, e null. Lowest -0.5, tied by b and c.
 */
class HighlightTest {

    private fun stats(
        team: String,
        small: Int = 0,
        grand: Int = 0,
        bombs: Int = 0,
        doubleWins: Int = 0,
        tichuCalls: Int = 0,
        destroyedTichus: Int = 0,
        beschissGames: Int = 0,
        goodHorseRatio: Double? = null,
    ) = TeamStatistics(
        teamId = TeamId(team),
        successfulSmallTichu = small,
        successfulGrandTichu = grand,
        bombCount = bombs,
        bombsPerGame = null,
        doubleWins = doubleWins,
        tichuCalls = tichuCalls,
        destroyedTichus = destroyedTichus,
        beschissGames = beschissGames,
        goodHorseRatio = goodHorseRatio,
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
                Highlight(HighlightMetric.SUCCESSFUL_TICHU, 3.0, listOf(TeamId("a"), TeamId("c"))),
                Highlight(HighlightMetric.BOMBS, 4.0, listOf(TeamId("a"), TeamId("b"))),
                Highlight(HighlightMetric.DOUBLE_WINS, 2.0, listOf(TeamId("b"), TeamId("c"))),
            ),
            highlights,
        )
    }

    @Test
    fun `a count metric whose maximum is zero has no highlight`() {
        val highlights = Highlight.of(
            listOf(
                stats("a", small = 1, bombs = 0, doubleWins = 0),
                stats("b", small = 0, bombs = 0, doubleWins = 0),
            ),
        )

        assertEquals(
            listOf(Highlight(HighlightMetric.SUCCESSFUL_TICHU, 1.0, listOf(TeamId("a")))),
            highlights,
        )
    }

    @Test
    fun `tichu calls, destroyed tichus and beschiss games lead on their highest count`() {
        val highlights = Highlight.of(
            listOf(
                stats("a", tichuCalls = 5, destroyedTichus = 0, beschissGames = 0),
                stats("b", tichuCalls = 2, destroyedTichus = 0, beschissGames = 0),
                stats("c", tichuCalls = 5, destroyedTichus = 1, beschissGames = 0),
                stats("d", tichuCalls = 0, destroyedTichus = 0, beschissGames = 0),
                stats("e", tichuCalls = 0, destroyedTichus = 1, beschissGames = 0),
            ),
        )

        assertEquals(
            listOf(
                Highlight(HighlightMetric.TICHU_CALLS, 5.0, listOf(TeamId("a"), TeamId("c"))),
                Highlight(HighlightMetric.DESTROYED_TICHUS, 1.0, listOf(TeamId("c"), TeamId("e"))),
            ),
            highlights,
        )
    }

    @Test
    fun `good horse leads with the lowest ratio, and a negative ratio beats a positive one`() {
        val highlights = Highlight.of(
            listOf(
                stats("a", goodHorseRatio = 1.2),
                stats("b", goodHorseRatio = -0.5),
                stats("c", goodHorseRatio = 0.0),
            ),
        )

        assertEquals(
            listOf(Highlight(HighlightMetric.GOOD_HORSE, -0.5, listOf(TeamId("b")))),
            highlights,
        )
    }

    @Test
    fun `good horse ties every team at the exact lowest ratio, in input order`() {
        val highlights = Highlight.of(
            listOf(
                stats("a", goodHorseRatio = 1.2),
                stats("b", goodHorseRatio = -0.5),
                stats("c", goodHorseRatio = -0.5),
                stats("d", goodHorseRatio = 0.0),
            ),
        )

        assertEquals(
            listOf(Highlight(HighlightMetric.GOOD_HORSE, -0.5, listOf(TeamId("b"), TeamId("c")))),
            highlights,
        )
    }

    @Test
    fun `a zero good horse ratio is a valid lowest ratio`() {
        val highlights = Highlight.of(
            listOf(
                stats("a", goodHorseRatio = 0.8),
                stats("b", goodHorseRatio = 0.0),
            ),
        )

        assertEquals(
            listOf(Highlight(HighlightMetric.GOOD_HORSE, 0.0, listOf(TeamId("b")))),
            highlights,
        )
    }

    @Test
    fun `good horse skips teams without a ratio`() {
        val highlights = Highlight.of(
            listOf(
                stats("a", goodHorseRatio = null),
                stats("b", goodHorseRatio = 0.8),
                stats("c", goodHorseRatio = null),
            ),
        )

        assertEquals(
            listOf(Highlight(HighlightMetric.GOOD_HORSE, 0.8, listOf(TeamId("b")))),
            highlights,
        )
    }

    @Test
    fun `good horse is absent when no team has a ratio`() {
        val highlights = Highlight.of(
            listOf(
                stats("a", goodHorseRatio = null),
                stats("b", goodHorseRatio = null),
            ),
        )

        assertEquals(emptyList(), highlights)
    }
}
