package com.example.template.domain

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
}
