package tichu.domain

/**
 * Outbound port (driven side of the hexagon). The domain declares what it needs;
 * an adapter in `infra/persistence` provides the implementation.
 */
interface TournamentRepository {

    fun save(tournament: Tournament): Tournament

    fun findById(id: TournamentId): Tournament?

    /**
     * Row-locks the tournament until the caller's transaction ends, so its round
     * advances run one at a time. Requires a transaction; a no-op when the tournament
     * does not exist.
     */
    fun lock(id: TournamentId)

    /** Only the caller's tournaments — the ownership scope is pushed into the query. */
    fun findAllByOwner(owner: AdminUserId): List<Tournament>

    /**
     * Removes the tournament. Every tournament-scoped child table added later must
     * reference `tournaments` with `ON DELETE CASCADE`, so deleting a tournament also
     * removes its teams, players, rounds and games.
     */
    fun delete(id: TournamentId)
}
