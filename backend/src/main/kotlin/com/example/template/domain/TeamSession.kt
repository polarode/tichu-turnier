package com.example.template.domain

import java.time.Instant

/**
 * A server-side session issued after a team exchanges its access link (Phase 5).
 * The principal is the team itself — no per-player identity exists. Only the hash
 * of the opaque token is stored; the raw token lives in the httpOnly team cookie
 * and is never recoverable from the database.
 */
data class TeamSession(
    val id: SessionId,
    val teamId: TeamId,
    val tokenHash: String,
    val createdAt: Instant,
    val expiresAt: Instant,
) {
    fun isExpired(now: Instant): Boolean = !expiresAt.isAfter(now)
}

/** Outbound port: persistence for [TeamSession]. Implemented in `infra/persistence`. */
interface TeamSessionRepository {

    fun save(session: TeamSession): TeamSession

    fun findByTokenHash(tokenHash: String): TeamSession?

    fun deleteByTokenHash(tokenHash: String)
}