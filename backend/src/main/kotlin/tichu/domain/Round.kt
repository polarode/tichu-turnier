package tichu.domain

/**
 * One iteration of the tournament: every team plays exactly one match, seated
 * according to the round's [pairing]. Created by the start-tournament use case
 * (round 1) and later by the round-advance flow (rounds 2+).
 */
data class Round(
    val id: RoundId,
    val tournamentId: TournamentId,
    /** 1-based round number within the tournament; round 1 is the first round. */
    val number: Int,
    val pairing: Pairing,
) {
    /** The table numbered [tableNumber] in this round's pairing; null when there is none. */
    fun table(tableNumber: Int): Table? = pairing.tables.firstOrNull { it.id.value == tableNumber }

    /** The table [teamId] is seated at this round; null when the team sits out. */
    fun tableOf(teamId: TeamId): Table? = pairing.tables.firstOrNull { it.seats(teamId) }

    /**
     * Whether every table of this round is completed, given the confirmed teams
     * per table (a table absent from [confirmations] has none). Round
     * advancement (S7) consumes this.
     */
    fun isComplete(confirmations: Map<TableId, Set<TeamId>>): Boolean =
        pairing.tables.all { TableConfirmations(it, confirmations[it.id].orEmpty()).isCompleted }
}

/** A typed id keeps domain signatures explicit and avoids passing bare strings around. */
@JvmInline
value class RoundId(val value: String)
