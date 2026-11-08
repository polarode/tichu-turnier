package tichu.domain

/**
 * One team's Team Statistics (GLOSSARY.md), derived at read time from the raw game
 * facts of completed tables only (ADR 0004). Team-only, never per player:
 * [successfulSmallTichu] and [successfulGrandTichu] sum the successful calls of the
 * team's two players, [bombCount] the bombs they played, [bombsPerGame] the bombs
 * per game over the completed rounds the team was seated in (null when there are
 * none), and [doubleWins] the games this team won as a Double Win.
 *
 * [tichuCalls] counts every small or grand call of the team's two players, successful
 * or not (Tichu Calls Made). [destroyedTichus] counts, per call, the opponents' failed
 * calls while a player of this team holds position 1 (Destroyed Tichu); a call broken
 * by the caller's own partner destroys nothing. [beschissGames] counts the games the
 * team played that were flagged Beschiss. [goodHorseRatio] is the summed Tichu Points
 * over the summed Victory Points of those games, unrounded, and null when the team has
 * no Victory Points (Good Horse Ratio).
 */
data class TeamStatistics(
    val teamId: TeamId,
    val successfulSmallTichu: Int,
    val successfulGrandTichu: Int,
    val bombCount: Int,
    val bombsPerGame: Double?,
    val doubleWins: Int,
    val tichuCalls: Int,
    val destroyedTichus: Int,
    val beschissGames: Int,
    val goodHorseRatio: Double?,
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
            val outcomes = games.map { it.score(settings) }
            val results = games.map { game -> game.teamResults.first { it.teamId == teamId } }
            val players = results.flatMap { it.players }
            val bombs = players.sumOf { it.bombs }
            val teamPoints = outcomes.sumOf { it.teamPoints.getValue(teamId) }
            val victoryPoints = outcomes.sumOf { it.victoryPoints.getValue(teamId) }
            return TeamStatistics(
                teamId = teamId,
                successfulSmallTichu = players.count { it.isSuccess && it.call == TichuCall.SMALL },
                successfulGrandTichu = players.count { it.isSuccess && it.call == TichuCall.GRAND },
                bombCount = bombs,
                bombsPerGame = if (seatedRounds == 0) null else bombs.toDouble() / (GAMES_PER_ROUND * seatedRounds),
                doubleWins = outcomes.count { it.doubleWinTeam == teamId },
                tichuCalls = players.count { it.call != TichuCall.NONE },
                destroyedTichus = games.sumOf { destroyedTichus(teamId, it) },
                beschissGames = games.count { it.beschiss },
                goodHorseRatio = if (victoryPoints == 0) null else teamPoints.toDouble() / victoryPoints,
            )
        }

        /**
         * The opponents' calls that [game] destroyed for [teamId]: every failed SMALL or
         * GRAND call of the other team, provided a player of [teamId] holds position 1.
         * A call broken by the caller's own partner going out first destroys nothing.
         */
        private fun destroyedTichus(teamId: TeamId, game: Game): Int {
            val own = game.teamResults.first { it.teamId == teamId }
            if (own.players.none { it.position == 1 }) return 0
            val opponent = game.teamResults.first { it.teamId != teamId }
            return opponent.players.count { it.call != TichuCall.NONE && !it.isSuccess }
        }
    }
}
