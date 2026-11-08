package tichu.domain

/** One point of a [TeamTrend]: the team's cumulative [victoryPoints] after the completed round [roundNumber]. */
data class TrendPoint(
    val roundNumber: Int,
    val victoryPoints: Int,
)

/**
 * One team's Team Trend (GLOSSARY.md): its cumulative Victory Points after each
 * completed round it was seated in, in ascending round order. The running total is
 * the same per-game VP the [Standings] sum, over the games of completed tables only.
 */
data class TeamTrend(
    val teamId: TeamId,
    val points: List<TrendPoint>,
) {

    companion object {

        /**
         * The Team Trend of every team in [teams], in the given order, over the
         * completed rounds of [rounds] (every table completed), scored with [settings].
         * A round that is not complete produces no point.
         */
        fun compute(teams: List<Team>, rounds: List<PlayedRound>, settings: TournamentSettings): List<TeamTrend> {
            val completedRounds = rounds
                .filter { it.round.isComplete(it.confirmations) }
                .sortedBy { it.round.number }
                .map { played ->
                    val completedTables = played.completedTables.map { it.id.value }.toSet()
                    played to played.games.filter { it.tableNumber in completedTables }.map { it.score(settings) }
                }
            return teams.map { team ->
                var running = 0
                val points = mutableListOf<TrendPoint>()
                for ((played, outcomes) in completedRounds) {
                    running += outcomes.totalFor(team.id).victoryPoints
                    if (played.round.tableOf(team.id) != null) {
                        points += TrendPoint(played.round.number, running)
                    }
                }
                TeamTrend(team.id, points)
            }
        }
    }
}
