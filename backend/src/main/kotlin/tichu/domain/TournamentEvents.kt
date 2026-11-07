package tichu.domain

/**
 * Outbound port for live tournament events. Events are thin invalidation signals
 * (ADR 0007): they say what changed, never carry scores, and clients refetch.
 * Implementations deliver them only after the surrounding transaction commits.
 */
interface TournamentEvents {
    fun publish(event: TournamentEvent)
}

data class TournamentEvent(
    val type: TournamentEventType,
    val tournamentId: TournamentId,
    val roundId: RoundId? = null,
    val tableId: TableId? = null,
)

enum class TournamentEventType {
    GAME_RECORDED,
    TABLE_CONFIRMED,
    CONFIRMATION_RETRACTED,
    TOURNAMENT_STARTED,
    ROUND_STARTED,
    TOTAL_ROUNDS_CHANGED,
    PUBLIC_VISIBILITY_CHANGED,
    TOURNAMENT_FINISHED,
    RESULTS_PUBLISHED,
}
