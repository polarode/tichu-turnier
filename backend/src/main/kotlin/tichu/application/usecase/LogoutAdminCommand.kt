package tichu.application.usecase

import tichu.application.api.LogoutAdmin
import tichu.domain.AdminSessionRepository
import tichu.domain.SessionTokens

/**
 * Deletes the session behind the presented token. Idempotent: a missing or already
 * deleted session is not an error, because logging out should always end in a clean,
 * unauthenticated state.
 */
class LogoutAdminCommand(
    private val sessions: AdminSessionRepository,
    private val sessionTokens: SessionTokens,
) : LogoutAdmin {

    override fun logout(rawToken: String) {
        sessions.deleteByTokenHash(sessionTokens.hash(rawToken))
    }
}
