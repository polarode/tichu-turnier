package com.example.template.application.usecase

import com.example.template.application.api.IssuedSession
import com.example.template.domain.AdminSession
import com.example.template.domain.AdminSessionRepository
import com.example.template.domain.AdminUser
import com.example.template.domain.IdGenerator
import com.example.template.domain.SessionId
import com.example.template.domain.SessionTokens
import java.time.Duration
import java.time.Instant

/**
 * Shared collaborator that turns a known admin into a persisted session and returns
 * the raw token plus its expiry. Kept separate from the use cases so that login and
 * refresh issue sessions through exactly the same path.
 */
class AdminSessionIssuer(
    private val sessions: AdminSessionRepository,
    private val sessionTokens: SessionTokens,
    private val idGenerator: IdGenerator,
    private val sessionTtl: Duration,
) {

    fun issue(user: AdminUser, now: Instant): IssuedSession {
        val rawToken = sessionTokens.generate()
        val session = AdminSession(
            id = SessionId(idGenerator.newId()),
            adminUserId = user.id,
            tokenHash = sessionTokens.hash(rawToken),
            createdAt = now,
            expiresAt = now.plus(sessionTtl),
        )
        sessions.save(session)
        return IssuedSession(
            adminUserId = user.id,
            email = user.email,
            token = rawToken,
            expiresAt = session.expiresAt,
        )
    }
}
