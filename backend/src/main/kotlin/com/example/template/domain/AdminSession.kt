package com.example.template.domain

import java.time.Instant

/**
 * A server-side session issued after an admin authenticates. Only the hash of the
 * opaque token is stored; the raw token lives in the httpOnly cookie and is never
 * recoverable from the database.
 */
data class AdminSession(
    val id: SessionId,
    val adminUserId: AdminUserId,
    val tokenHash: String,
    val createdAt: Instant,
    val expiresAt: Instant,
) {
    fun isExpired(now: Instant): Boolean = !expiresAt.isAfter(now)
}

/** A typed id keeps domain signatures explicit and avoids passing bare strings around. */
@JvmInline
value class SessionId(val value: String)

/** Outbound port: persistence for [AdminSession]. Implemented in `infra/persistence`. */
interface AdminSessionRepository {

    fun save(session: AdminSession): AdminSession

    fun findByTokenHash(tokenHash: String): AdminSession?

    fun deleteByTokenHash(tokenHash: String)
}
