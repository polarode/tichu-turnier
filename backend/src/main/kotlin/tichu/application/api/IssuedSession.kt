package tichu.application.api

import tichu.domain.AdminRole
import tichu.domain.AdminUserId
import java.time.Instant

/**
 * The result of a successful login or refresh. [token] is the raw opaque token that
 * the web adapter puts into the session cookie — it is never returned in a response
 * body and never logged.
 */
data class IssuedSession(
    val adminUserId: AdminUserId,
    val email: String,
    /**
     * The account's role, surfaced to the client so an owner-only console can be shown
     * only to owners. It is a read model, never an authorization decision — the backend
     * re-checks the role on every request.
     */
    val role: AdminRole,
    val token: String,
    val expiresAt: Instant,
)
