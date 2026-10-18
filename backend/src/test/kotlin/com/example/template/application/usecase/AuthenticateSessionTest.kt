package com.example.template.application.usecase

import com.example.template.domain.AdminRole
import com.example.template.domain.AdminSession
import com.example.template.domain.AdminStatus
import com.example.template.domain.AdminUser
import com.example.template.domain.AdminUserId
import com.example.template.domain.SessionId
import com.example.template.domain.SessionTokens
import com.example.template.testsupport.InMemoryAdminSessionRepository
import com.example.template.testsupport.InMemoryAdminUserRepository
import com.example.template.vocabulary.UnauthenticatedException
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Pure unit test: no Spring context. Proves the read-only session resolution used by
 * the API-wide guard accepts exactly the sessions that should authenticate.
 */
class AuthenticateSessionTest {

    private val now = Instant.parse("2026-01-01T12:00:00Z")
    private val fixedClock = Clock.fixed(now, ZoneOffset.UTC)

    private val users = InMemoryAdminUserRepository()
    private val sessions = InMemoryAdminSessionRepository()
    private val sessionTokens = object : SessionTokens {
        override fun generate(): String = "token"
        override fun hash(token: String): String = "hash:$token"
    }

    private val authenticateSession = AuthenticateSessionQuery(sessions, users, sessionTokens, fixedClock)

    private val user = AdminUser(
        id = AdminUserId("admin-1"),
        email = "ada@example.com",
        passwordHash = "irrelevant",
        status = AdminStatus.ACTIVE,
        role = AdminRole.ADMIN,
        createdAt = now,
    )

    private fun storeSession(rawToken: String, expiresAt: Instant = now.plus(Duration.ofDays(7))) {
        users.save(user)
        sessions.save(
            AdminSession(
                id = SessionId("session-$rawToken"),
                adminUserId = user.id,
                tokenHash = sessionTokens.hash(rawToken),
                createdAt = now,
                expiresAt = expiresAt,
            ),
        )
    }

    @Test
    fun `resolves a valid session to its admin without rotating it`() {
        storeSession("valid-token")

        val authenticated = authenticateSession.authenticate("valid-token")

        assertEquals(user.id, authenticated.adminUserId)
        assertEquals(user.email, authenticated.email)
        // Read-only: the session must still be present for the request that follows.
        assertEquals(
            user.id,
            sessions.findByTokenHash(sessionTokens.hash("valid-token"))?.adminUserId,
            "authentication must not rotate or delete the session",
        )
    }

    @Test
    fun `rejects an unknown session`() {
        assertFailsWith<UnauthenticatedException> {
            authenticateSession.authenticate("never-issued")
        }
    }

    @Test
    fun `rejects an expired session`() {
        storeSession("expired-token", expiresAt = now.minus(Duration.ofSeconds(1)))

        assertFailsWith<UnauthenticatedException> {
            authenticateSession.authenticate("expired-token")
        }
    }

    @Test
    fun `rejects a session whose admin no longer exists`() {
        sessions.save(
            AdminSession(
                id = SessionId("orphan-session"),
                adminUserId = AdminUserId("deleted-admin"),
                tokenHash = sessionTokens.hash("orphan-token"),
                createdAt = now,
                expiresAt = now.plus(Duration.ofDays(7)),
            ),
        )

        assertFailsWith<UnauthenticatedException> {
            authenticateSession.authenticate("orphan-token")
        }
    }

    @Test
    fun `rejects a session whose admin is no longer active`() {
        storeSession("revoked-token")
        users.save(user.copy(status = AdminStatus.DISABLED))

        assertFailsWith<UnauthenticatedException> {
            authenticateSession.authenticate("revoked-token")
        }
    }
}
