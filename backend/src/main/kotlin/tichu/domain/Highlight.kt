package tichu.domain

/** The team statistics a [Highlight] can lead (GLOSSARY.md: **Highlight**). */
enum class HighlightMetric {
    /** Successful small and grand Tichu calls, summed. */
    SUCCESSFUL_TICHU,

    /** Bombs played. */
    BOMBS,

    /** Games won as a Double Win. */
    DOUBLE_WINS,
}

/**
 * The team(s) leading one [metric] of the tournament statistics (GLOSSARY.md:
 * **Highlight**): every team tied at the maximum, in input order, with that
 * maximum [value]. A metric whose maximum is 0 has no highlight.
 */
data class Highlight(
    val metric: HighlightMetric,
    val value: Int,
    val teams: List<TeamId>,
) {

    companion object {

        /** The highlights of [stats], one per metric that has a non-zero maximum. */
        fun of(stats: List<TeamStatistics>): List<Highlight> =
            HighlightMetric.entries.mapNotNull { metric ->
                val valueOf: (TeamStatistics) -> Int = when (metric) {
                    HighlightMetric.SUCCESSFUL_TICHU -> { team -> team.successfulSmallTichu + team.successfulGrandTichu }
                    HighlightMetric.BOMBS -> { team -> team.bombCount }
                    HighlightMetric.DOUBLE_WINS -> { team -> team.doubleWins }
                }
                val max = stats.maxOfOrNull(valueOf) ?: 0
                if (max == 0) {
                    null
                } else {
                    Highlight(metric, max, stats.filter { valueOf(it) == max }.map { it.teamId })
                }
            }
    }
}
