package com.example.template.domain

import java.time.Instant

/**
 * The one secret team access token per team (Phase 5). The raw 256-bit token
 * generated through the [SessionTokens] port exists only in the copyable link
 * handed to the organizer; this entity carries nothing but its SHA-256 digest,
 * so the database can never leak the link.
 *
 * Lifecycle: `unrevealed → revealed → (rotated → revealed) / revoked`. A token
 * whose [revokedAt] is set is dead immediately, regardless of live sessions.
 */
data class TeamToken(
    /** The team this token belongs to; exactly one token row exists per team. */
    val teamId: TeamId,
    val tokenHash: String,
    /** Whether the organizer has been shown the raw link at least once. */
    val revealed: Boolean,
    /** Set the moment the token is revoked; null while the token is live. */
    val revokedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    fun isRevoked(): Boolean = revokedAt != null
}

/** Outbound port: persistence for [TeamToken]. Implemented in `infra/persistence`. */
interface TeamTokenRepository {

    fun save(token: TeamToken): TeamToken

    /** Lookups are by team or by digest only — never by anything reversible. */
    fun findByTeamId(teamId: TeamId): TeamToken?

    /** Token secrets are looked up by hash only; the raw token is never stored. */
    fun findByTokenHash(tokenHash: String): TeamToken?

    fun deleteByTeamId(teamId: TeamId)
}