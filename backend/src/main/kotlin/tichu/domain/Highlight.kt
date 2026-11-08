package tichu.domain

/** Which end of a metric's values leads a [Highlight]. */
enum class Lead {
    /** The highest value leads. */
    HIGHEST,

    /** The lowest value leads. */
    LOWEST,
}

/**
 * The team statistics a [Highlight] can lead (GLOSSARY.md: **Highlight**), each with
 * the [selector] reading its value from a team's statistics (null when the team has
 * no value) and the [lead] that decides which value leads.
 */
enum class HighlightMetric(
    internal val selector: (TeamStatistics) -> Double?,
    internal val lead: Lead,
) {
    /** Successful small and grand Tichu calls, summed. */
    SUCCESSFUL_TICHU(
        { (it.successfulSmallTichu + it.successfulGrandTichu).toDouble() },
        Lead.HIGHEST,
    ),

    /** Bombs played. */
    BOMBS({ it.bombCount.toDouble() }, Lead.HIGHEST),

    /** Games won as a Double Win. */
    DOUBLE_WINS({ it.doubleWins.toDouble() }, Lead.HIGHEST),

    /** Small and grand Tichu calls made, successful or not (Tichu Calls Made). */
    TICHU_CALLS({ it.tichuCalls.toDouble() }, Lead.HIGHEST),

    /** Opponents' failed Tichu calls destroyed while holding position 1 (Destroyed Tichu). */
    DESTROYED_TICHUS({ it.destroyedTichus.toDouble() }, Lead.HIGHEST),

    /** Games flagged Beschiss. */
    BESCHISS_GAMES({ it.beschissGames.toDouble() }, Lead.HIGHEST),

    /** Tichu Points over Victory Points (Good Horse Ratio); the lowest ratio leads. */
    GOOD_HORSE({ it.goodHorseRatio }, Lead.LOWEST),
}

/**
 * The team(s) leading one [metric] of the tournament statistics (GLOSSARY.md:
 * **Highlight**): every team tied at the leading [value], in input order. Leading means
 * the highest value, except for the Good Horse Ratio, where it means the lowest non-null
 * ratio; teams without a value are never shown. A count metric whose maximum is 0 has no
 * highlight, and the Good Horse has none when no team has a ratio. Zero and negative
 * Good Horse ratios are valid and can lead.
 */
data class Highlight(
    val metric: HighlightMetric,
    val value: Double,
    val teams: List<TeamId>,
) {

    companion object {

        /** The highlights of [stats], one per metric that has a leading value, in metric order. */
        fun of(stats: List<TeamStatistics>): List<Highlight> =
            HighlightMetric.entries.mapNotNull { metric -> leading(metric, stats) }

        private fun leading(metric: HighlightMetric, stats: List<TeamStatistics>): Highlight? {
            val valued = stats.mapNotNull { team -> metric.selector(team)?.let { value -> team.teamId to value } }
            val leadingValue = when (metric.lead) {
                Lead.HIGHEST -> valued.maxOfOrNull { (_, value) -> value }?.takeUnless { it == 0.0 }
                Lead.LOWEST -> valued.minOfOrNull { (_, value) -> value }
            } ?: return null
            val leaders = valued.filter { (_, value) -> value == leadingValue }.map { (teamId, _) -> teamId }
            return Highlight(metric, leadingValue, leaders)
        }
    }
}
