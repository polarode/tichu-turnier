package tichu.domain

/** One team's Table Totals (GLOSSARY.md): its summed [victoryPoints] and [tichuPoints] at one table of one round. */
data class TableTotal(val victoryPoints: Int, val tichuPoints: Int)

/**
 * The Table Totals (GLOSSARY.md): per completed table of a round, each seated
 * team's summed Victory Points and Tichu Points over the games it played there.
 * Pure and framework-free; completion follows the same rule as the [Standings].
 */
object TableTotals {

    /**
     * For each completed table of [played] (every seated team confirmed), each
     * seated team's summed VP and TP over the games it played there, scored with
     * [settings]. A trio team's totals cover only its own games. Tables that are
     * not completed are absent; a seated team with no games is listed with zeros.
     */
    fun of(played: PlayedRound, settings: TournamentSettings): Map<TableId, Map<TeamId, TableTotal>> =
        played.round.pairing.tables
            .filter { TableConfirmations(it, played.confirmations[it.id].orEmpty()).isCompleted }
            .associate { table ->
                val outcomes = played.games
                    .filter { it.tableNumber == table.id.value }
                    .map { it.score(settings) }
                table.id to table.seatedTeams.associateWith { team ->
                    val teamOutcomes = outcomes.filter { team in it.victoryPoints }
                    TableTotal(
                        victoryPoints = teamOutcomes.sumOf { it.victoryPoints.getValue(team) },
                        tichuPoints = teamOutcomes.sumOf { it.teamPoints.getValue(team) },
                    )
                }
            }
}
