package tichu.domain

/** One team's Table Totals (GLOSSARY.md): its summed [victoryPoints] and [tichuPoints] at one table of one round. */
data class TableTotal(val victoryPoints: Int, val tichuPoints: Int)

/** The [outcomes] among these that [team] played: a game it sat out has no entry in its VP map. */
internal fun List<GameOutcome>.playedBy(team: TeamId): List<GameOutcome> =
    filter { team in it.victoryPoints }

/** [team]'s summed VP and TP over the games it played among these [GameOutcome]s; zeros when it played none. */
internal fun List<GameOutcome>.totalFor(team: TeamId): TableTotal {
    val played = playedBy(team)
    return TableTotal(
        victoryPoints = played.sumOf { it.victoryPoints.getValue(team) },
        tichuPoints = played.sumOf { it.teamPoints.getValue(team) },
    )
}

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
        played.completedTables.associate { table ->
            val outcomes = played.games
                .filter { it.tableNumber == table.id.value }
                .map { it.score(settings) }
            table.id to table.seatedTeams.associateWith { team -> outcomes.totalFor(team) }
        }
}
