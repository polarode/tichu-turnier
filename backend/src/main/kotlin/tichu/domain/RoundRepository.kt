package tichu.domain

/**
 * Outbound port (driven side of the hexagon). The domain declares what it needs;
 * an adapter in `infra/persistence` provides the implementation.
 */
interface RoundRepository {

    /** Persists one round together with its tables (one aggregate, one transaction). */
    fun save(round: Round): Round

    /** Exact lookup by tournament and 1-based round number; null when absent. */
    fun findByTournamentAndNumber(tournamentId: TournamentId, number: Int): Round?

    /** All rounds of one tournament, ordered by round number. */
    fun findByTournament(tournamentId: TournamentId): List<Round>

    /**
     * Takes an exclusive lock on one round table for the rest of the caller's
     * transaction, so confirm, retract and game writes on that table run one at
     * a time (ADR 0006). Requires a transaction; a no-op when the table is absent.
     */
    fun lockTable(key: TableKey)
}
