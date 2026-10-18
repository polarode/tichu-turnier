package com.example.template.domain

/**
 * Outbound port (driven side of the hexagon). The domain declares what it needs;
 * an adapter in `infra/persistence` provides the implementation.
 *
 * [Team] is the aggregate root: saving a team also persists its two players.
 * Every lookup is scoped to a [TournamentId] — teams never exist outside a
 * tournament, and the ownership check happens one layer up via
 * `TournamentRepository.findOwnedOrThrow` before these methods are reached.
 */
interface TeamRepository {

    /**
     * Persists the team together with its players (one aggregate, one transaction).
     * For an existing team this replaces its players, so a rename or a player swap
     * does not leave orphaned rows behind.
     */
    fun save(team: Team): Team

    /** All teams of one tournament, each with its players. */
    fun findAllByTournament(tournamentId: TournamentId): List<Team>

    /** Number of teams already registered in one tournament — used by the capacity rule. */
    fun countByTournament(tournamentId: TournamentId): Int

    /** Exact id lookup inside one tournament — returns null for foreign/missing ids. */
    fun findById(teamId: TeamId, tournamentId: TournamentId): Team?

    /**
     * Unscoped exact lookup by id. Not an ownership path: callers must have already
     * validated a secret or an ownership claim that pins the team. Used by the
     * team-token exchange, which resolves the token's own team after validating it.
     */
    fun findById(teamId: TeamId): Team?

    /** Removes the team; its players go with it (ON DELETE CASCADE in the schema). */
    fun delete(teamId: TeamId, tournamentId: TournamentId)
}
