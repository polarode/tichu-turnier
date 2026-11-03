package tichu.domain

/**
 * One round as the standings read it: its [round] pairing, the [games] entered
 * in it and the confirmed teams per table ([confirmations]; a table absent from
 * it has none). Only completed tables count.
 */
data class PlayedRound(
    val round: Round,
    val games: List<Game>,
    val confirmations: Map<TableId, Set<TeamId>>,
)

/** One team's summed results: the input [Standings.rank] orders and ranks. */
data class TeamTotals(
    val teamId: TeamId,
    val teamName: String,
    val gamesPlayed: Int,
    val victoryPoints: Int,
    val tichuPoints: Int,
)

/** One line of the [Standings]: the [team]'s summed results and the [rank] they earn. */
data class StandingsEntry(
    val rank: Int,
    val team: TeamTotals,
)

/**
 * The Standings (GLOSSARY.md): every team ordered by summed Victory Points, then
 * summed Tichu Points, over the games of completed tables only. Pure and
 * framework-free; the one ordering that both the standings read and the Swiss
 * pairing of the next round use.
 */
data class Standings(val entries: List<StandingsEntry>) {

    companion object {

        /**
         * Sums every team's games at the completed tables of [rounds], scored with
         * [settings], and ranks the result. Teams without a completed game are
         * listed with zeros.
         */
        fun compute(teams: List<Team>, rounds: List<PlayedRound>, settings: TournamentSettings): Standings {
            val outcomes = rounds.flatMap { played ->
                val completedTables = played.round.pairing.tables
                    .filter { TableConfirmations(it, played.confirmations[it.id].orEmpty()).isCompleted }
                    .map { it.id.value }
                    .toSet()
                played.games.filter { it.tableNumber in completedTables }.map { it.score(settings) }
            }
            return rank(
                teams.map { team ->
                    val played = outcomes.filter { team.id in it.victoryPoints }
                    TeamTotals(
                        teamId = team.id,
                        teamName = team.name,
                        gamesPlayed = played.size,
                        victoryPoints = played.sumOf { it.victoryPoints.getValue(team.id) },
                        tichuPoints = played.sumOf { it.teamPoints.getValue(team.id) },
                    )
                },
            )
        }

        /**
         * Orders [totals] by VP desc, then TP desc, then team name (case-insensitive),
         * and ranks them: a team's rank is 1 + the number of teams strictly ahead,
         * so exact VP/TP ties share a rank and the next one skips (1, 2, 2, 4).
         */
        fun rank(totals: List<TeamTotals>): Standings {
            val ordered = totals.sortedWith(
                compareByDescending<TeamTotals> { it.victoryPoints }
                    .thenByDescending { it.tichuPoints }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.teamName },
            )
            return Standings(
                ordered.map { team ->
                    val ahead = ordered.indexOfFirst {
                        it.victoryPoints == team.victoryPoints && it.tichuPoints == team.tichuPoints
                    }
                    StandingsEntry(ahead + 1, team)
                },
            )
        }
    }
}
