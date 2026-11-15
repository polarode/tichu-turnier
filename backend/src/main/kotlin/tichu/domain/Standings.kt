package tichu.domain

/**
 * One round as the standings read it: its [round] pairing, the [games] entered
 * in it, the confirmed teams per table ([confirmations]; a table absent from
 * it has none) and every [withdrawals] of the tournament (ADR 0011). Only
 * completed tables count.
 */
data class PlayedRound(
    val round: Round,
    val games: List<Game>,
    val confirmations: Map<TableId, Set<TeamId>>,
    /** The tournament's withdrawn teams; a withdrawn team's confirmation is not needed. */
    val withdrawals: Map<TeamId, Withdrawal> = emptyMap(),
) {
    /** The tables of [round] that are completed; the only ones whose games count. */
    val completedTables: List<Table>
        get() = round.pairing.tables.filter { round.isTableCompleted(it, confirmations, withdrawals.keys) }

    /**
     * The games this round's withdrawals leave unplayed (ADR 0011): at each table
     * a withdrawn team of this round sits at, every scheduled game that has no
     * entered game and includes a withdrawn team. It is awarded to the other team
     * unless that one withdrew too. Ordered by table, then game number.
     */
    val forfeits: List<ForfeitGame>
        get() {
            val withdrawnHere = withdrawals.filterValues { it.roundNumber == round.number }
            if (withdrawnHere.isEmpty()) return emptyList()
            val entered = games.map { it.tableNumber to it.gameNumber }.toSet()
            return round.pairing.tables
                .filter { table -> withdrawnHere.keys.any { table.seats(it) } }
                .flatMap { table ->
                    table.schedule
                        .filter { (table.id.value to it.gameNumber) !in entered }
                        .filter { scheduled -> scheduled.playingTeams.any { it in withdrawnHere } }
                        .map { scheduled ->
                            val withdrawnTeam = scheduled.playingTeams.first { it in withdrawnHere }
                            val opponent = scheduled.playingTeams.first { it != withdrawnTeam }
                            ForfeitGame(
                                roundNumber = round.number,
                                tableNumber = table.id.value,
                                gameNumber = scheduled.gameNumber,
                                withdrawnTeam = withdrawnTeam,
                                opponent = opponent,
                                awarded = opponent !in withdrawnHere,
                                compensation = withdrawnHere.getValue(withdrawnTeam).compensation,
                            )
                        }
                }
                .sortedWith(compareBy({ it.tableNumber }, { it.gameNumber }))
        }

    /** The forfeits at completed tables: only those count, like games do. */
    val completedForfeits: List<ForfeitGame>
        get() {
            val completed = completedTables.map { it.id.value }.toSet()
            return forfeits.filter { it.tableNumber in completed }
        }

    /**
     * The VP and TP outcomes this round counts toward the standings: every game at a
     * completed table scored with [settings], then every Forfeit Game at one (ADR 0011).
     */
    fun completedOutcomes(settings: TournamentSettings): List<GameOutcome> {
        val completed = completedTables.map { it.id.value }.toSet()
        return games.filter { it.tableNumber in completed }.map { it.score(settings) } +
            completedForfeits.map { it.outcome() }
    }
}

/**
 * One team's summed results: the input [Standings.rank] orders and ranks. [gamesPlayed]
 * counts played games only, never Forfeit Games (ADR 0011). [withdrawalReason] is set
 * once the team withdrew, so the standings can mark it.
 */
data class TeamTotals(
    val teamId: TeamId,
    val teamName: String,
    val gamesPlayed: Int,
    val victoryPoints: Int,
    val tichuPoints: Int,
    val withdrawalReason: WithdrawalReason? = null,
) {
    /** Whether the team was disqualified (ADR 0011); the one rule the ranking and the entries share. */
    val isDisqualified: Boolean
        get() = withdrawalReason == WithdrawalReason.DISQUALIFIED
}

/**
 * One line of the [Standings]: the [team]'s summed results and the [rank] they earn.
 * A disqualified team has no [rank] (null) and is listed after all others.
 */
data class StandingsEntry(
    val rank: Int?,
    val team: TeamTotals,
) {
    /** Whether the team was disqualified; such an entry has no [rank]. */
    val isDisqualified: Boolean
        get() = team.isDisqualified
}

/**
 * The Standings (GLOSSARY.md): every team ordered by summed Victory Points, then
 * summed Tichu Points, over the games of completed tables only. Pure and
 * framework-free; the one ordering that both the standings read and the Swiss
 * pairing of the next round use.
 */
data class Standings(val entries: List<StandingsEntry>) {

    companion object {

        /**
         * Sums every team's games at the completed tables of [rounds], plus the Forfeit
         * Games at those tables (ADR 0011), scored with [settings], and ranks the result.
         * Only played games count toward gamesPlayed. Teams without a completed game are
         * listed with zeros; a withdrawn team carries its reason from [Team.withdrawal].
         */
        fun compute(teams: List<Team>, rounds: List<PlayedRound>, settings: TournamentSettings): Standings {
            val playedOutcomes = rounds.flatMap { played ->
                val completedTables = played.completedTables.map { it.id.value }.toSet()
                played.games.filter { it.tableNumber in completedTables }.map { it.score(settings) }
            }
            val forfeitOutcomes = rounds.flatMap { played -> played.completedForfeits.map { it.outcome() } }
            val allOutcomes = playedOutcomes + forfeitOutcomes
            return rank(
                teams.map { team ->
                    val total = allOutcomes.totalFor(team.id)
                    TeamTotals(
                        teamId = team.id,
                        teamName = team.name,
                        gamesPlayed = playedOutcomes.playedBy(team.id).size,
                        victoryPoints = total.victoryPoints,
                        tichuPoints = total.tichuPoints,
                        withdrawalReason = team.withdrawal?.reason,
                    )
                },
            )
        }

        /**
         * Orders [totals] by VP desc, then TP desc, then team name (case-insensitive),
         * and ranks them: a team's rank is 1 + the number of teams strictly ahead,
         * so exact VP/TP ties share a rank and the next one skips (1, 2, 2, 4).
         * Disqualified teams are ordered the same way among themselves, but they rank
         * after every other team and get no rank (null). Other withdrawn teams rank
         * normally (ADR 0011).
         */
        fun rank(totals: List<TeamTotals>): Standings {
            val (disqualified, ranked) = totals.partition { it.isDisqualified }
            val ordered = orderedByTotals(ranked)
            val rankedEntries = ordered.map { team ->
                val ahead = ordered.indexOfFirst {
                    it.victoryPoints == team.victoryPoints && it.tichuPoints == team.tichuPoints
                }
                StandingsEntry(ahead + 1, team)
            }
            return Standings(rankedEntries + orderedByTotals(disqualified).map { StandingsEntry(null, it) })
        }

        /** The VP desc, TP desc, name order shared by ranked and disqualified teams. */
        private fun orderedByTotals(totals: List<TeamTotals>): List<TeamTotals> = totals.sortedWith(
            compareByDescending<TeamTotals> { it.victoryPoints }
                .thenByDescending { it.tichuPoints }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.teamName },
        )
    }
}
