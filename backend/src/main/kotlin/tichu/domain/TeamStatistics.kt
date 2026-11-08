package tichu.domain

/**
 * One team's Team Statistics (GLOSSARY.md), derived at read time from the raw game
 * facts of completed tables only (ADR 0004). Team-only, never per player:
 * [successfulSmallTichu] and [successfulGrandTichu] sum the successful calls of the
 * team's two players, [bombCount] the bombs they played, [bombsPerGame] the bombs
 * per game over the completed rounds the team was seated in (null when there are
 * none), and [doubleWins] the games this team won as a Double Win.
 */
data class TeamStatistics(
    val teamId: TeamId,
    val successfulSmallTichu: Int,
    val successfulGrandTichu: Int,
    val bombCount: Int,
    val bombsPerGame: Double?,
    val doubleWins: Int,
) {

    companion object {

        /** Every team plays exactly four games per round, trio rounds included. */
        private const val GAMES_PER_ROUND = 4

        /**
         * The statistics of every team in [teams], in the given order, over the games
         * of the completed tables of [rounds], scored with [settings].
         */
        fun compute(teams: List<Team>, rounds: List<PlayedRound>, settings: TournamentSettings): List<TeamStatistics> =
            teams.map { team -> of(team.id, rounds, settings) }

        private fun of(teamId: TeamId, rounds: List<PlayedRound>, settings: TournamentSettings): TeamStatistics {
            val seatedRounds = rounds.count { played -> played.completedTables.any { it.seats(teamId) } }
            val games = rounds.flatMap { played ->
                val completedTables = played.completedTables.map { it.id.value }.toSet()
                played.games.filter { it.tableNumber in completedTables && teamId in listOf(it.team1, it.team2) }
            }
            val results = games.map { game -> game.teamResults.first { it.teamId == teamId } }
            val players = results.flatMap { it.players }
            val bombs = players.sumOf { it.bombs }
            return TeamStatistics(
                teamId = teamId,
                successfulSmallTichu = players.count { it.isSuccess && it.call == TichuCall.SMALL },
                successfulGrandTichu = players.count { it.isSuccess && it.call == TichuCall.GRAND },
                bombCount = bombs,
                bombsPerGame = if (seatedRounds == 0) null else bombs.toDouble() / (GAMES_PER_ROUND * seatedRounds),
                doubleWins = games.count { it.score(settings).doubleWinTeam == teamId },
            )
        }
    }
}
