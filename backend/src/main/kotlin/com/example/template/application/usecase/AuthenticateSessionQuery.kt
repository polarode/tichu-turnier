package com.example.template.application.usecase

import com.example.template.application.api.AuthenticateSession
import com.example.template.application.api.AuthenticatedAdmin
import com.example.template.domain.AdminSessionRepository
import com.example.template.domain.AdminUserRepository
import com.example.template.domain.SessionTokens
import com.example.template.vocabulary.UnauthenticatedException
import java.time.Clock

/**
 * Resolves a raw session token to its admin for the API-wide guard. Read-only by
 * design: unlike refresh it never mutates the session, so a single token can
 * authenticate many requests until logout/expiry.
 */
class AuthenticateSessionQuery(
    private val sessions: AdminSessionRepository,
    private val adminUsers: AdminUserRepository,
    private val sessionTokens: SessionTokens,
    private val clock: Clock,
) : AuthenticateSession {

    override fun authenticate(rawToken: String): AuthenticatedAdmin {
        val session = sessions.findByTokenHash(sessionTokens.hash(rawToken))
            ?: throw unauthenticated()
        if (session.isExpired(clock.instant())) {
            throw unauthenticated()
        }
        val user = adminUsers.findById(session.adminUserId) ?: throw unauthenticated()
        // Status is re-read on every request, so disabling an admin kills the live
        // session on its very next call rather than only at the next login.
        if (!user.isActive()) throw unauthenticated()
        return AuthenticatedAdmin(adminUserId = user.id, email = user.email, role = user.role)
    }

    private fun unauthenticated() = UnauthenticatedException("Session is invalid or has expired")
}
