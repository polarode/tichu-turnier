package tichu.application.usecase

import tichu.application.api.IssuedSession
import tichu.application.api.RefreshSession
import tichu.domain.AdminSessionRepository
import tichu.domain.AdminUserRepository
import tichu.domain.SessionTokens
import tichu.vocabulary.UnauthenticatedException
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * Rotates a valid session: the presented token is invalidated and a new one issued in
 * the same transaction, so a refresh either fully succeeds or leaves the old session
 * untouched.
 */
@Transactional
class RefreshSessionCommand(
    private val sessions: AdminSessionRepository,
    private val adminUsers: AdminUserRepository,
    private val sessionTokens: SessionTokens,
    private val sessionIssuer: AdminSessionIssuer,
    private val clock: Clock,
) : RefreshSession {

    override fun refresh(rawToken: String): IssuedSession {
        val now = clock.instant()
        val session = sessions.findByTokenHash(sessionTokens.hash(rawToken))
            ?: throw UnauthenticatedException("Session is invalid or has expired")
        if (session.isExpired(now)) {
            throw UnauthenticatedException("Session is invalid or has expired")
        }
        val user = adminUsers.findById(session.adminUserId)
            ?: throw UnauthenticatedException("Session is invalid or has expired")

        sessions.deleteByTokenHash(session.tokenHash)
        return sessionIssuer.issue(user, now)
    }
}
