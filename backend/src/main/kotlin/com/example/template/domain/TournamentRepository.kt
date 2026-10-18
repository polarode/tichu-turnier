package com.example.template.domain

/**
 * Outbound port (driven side of the hexagon). The domain declares what it needs;
 * an adapter in `infra/persistence` provides the implementation.
 */
interface TournamentRepository {

    fun save(tournament: Tournament): Tournament

    fun findById(id: TournamentId): Tournament?

    /** Only the caller's tournaments — the ownership scope is pushed into the query. */
    fun findAllByOwner(owner: AdminUserId): List<Tournament>

    /**
     * Removes the tournament. Every tournament-scoped child table added later must
     * reference `tournaments` with `ON DELETE CASCADE`, so deleting a tournament also
     * removes its teams, players, rounds and games.
     */
    fun delete(id: TournamentId)
}
