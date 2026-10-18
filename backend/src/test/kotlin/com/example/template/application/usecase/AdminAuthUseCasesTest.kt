package com.example.template.application.usecase

import com.example.template.application.api.LoginAdmin
import com.example.template.application.api.RegisterAdmin
import com.example.template.domain.AdminSession
import com.example.template.domain.IdGenerator
import com.example.template.domain.SessionId
import com.example.template.domain.SessionTokens
import com.example.template.infra.security.BcryptPasswordHasher
import com.example.template.testsupport.InMemoryAdminSessionRepository
import com.example.template.testsupport.InMemoryAdminUserRepository
import com.example.template.vocabulary.EmailAlreadyRegisteredException
import com.example.template.vocabulary.InvalidCredentialsException
import com.example.template.vocabulary.UnauthenticatedException
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Pure unit test: no Spring context. Ports are satisfied with in-memory repositories
 * and small fakes; the password hasher is the real bcrypt adapter so the "never store
 * plaintext" rule is exercised against real hashing.
 */
class AdminAuthUseCasesTest {

    private val now = Instant.parse("2026-01-01T12:00:00Z")
    private val fixedClock = Clock.fixed(now, ZoneOffset.UTC)
    private val ttl = Duration.ofDays(7)

    private val users = InMemoryAdminUserRepository()
    private val sessions = InMemoryAdminSessionRepository()
    private val passwordHasher = BcryptPasswordHasher()
    private val sessionTokens = object : SessionTokens {
        private var counter = 0
        override fun generate(): String = "token-${++counter}"
        override fun hash(token: String): String = "hash:$token"
    }
    private val idGenerator = object : IdGenerator {
        private var counter = 0
        override fun newId(): String = "id-${++counter}"
    }

    private val sessionIssuer = AdminSessionIssuer(sessions, sessionTokens, idGenerator, ttl)
    private val registerAdmin = RegisterAdminCommand(users, passwordHasher, idGenerator, fixedClock)
    private val loginAdmin = LoginAdminCommand(users, passwordHasher, sessionIssuer, fixedClock)
    private val refreshSession = RefreshSessionCommand(sessions, users, sessionTokens, sessionIssuer, fixedClock)
    private val logoutAdmin = LogoutAdminCommand(sessions, sessionTokens)

    @Test
    fun `register stores the account with a hashed password and normalized email`() {
        val user = registerAdmin.register(RegisterAdmin.RegisterAdminRequest("  Ada@Example.com ", "s3cret-pw"))

        assertEquals("ada@example.com", user.email, "email is trimmed and lowercased")
        assertNotEquals("s3cret-pw", user.passwordHash, "the raw password is never stored")
        assertTrue(passwordHasher.matches("s3cret-pw", user.passwordHash), "the stored hash verifies the password")
        assertEquals(now, user.createdAt)
    }

    @Test
    fun `register rejects a duplicate email regardless of case`() {
        registerAdmin.register(RegisterAdmin.RegisterAdminRequest("ada@example.com", "s3cret-pw"))

        assertFailsWith<EmailAlreadyRegisteredException> {
            registerAdmin.register(RegisterAdmin.RegisterAdminRequest("ADA@Example.com", "other-pw"))
        }
    }

    @Test
    fun `login issues a session whose raw token is never stored`() {
        registerAdmin.register(RegisterAdmin.RegisterAdminRequest("ada@example.com", "s3cret-pw"))

        val issued = loginAdmin.login(LoginAdmin.LoginAdminRequest("ada@example.com", "s3cret-pw"))

        assertTrue(issued.token.isNotBlank())
        assertEquals(now.plus(ttl), issued.expiresAt)
        assertEquals("ada@example.com", issued.email)
        val stored = sessions.findByTokenHash(sessionTokens.hash(issued.token))
        assertTrue(stored != null, "the session is persisted under its token hash")
        assertNotEquals(issued.token, stored!!.tokenHash, "only the hash is stored, not the raw token")
    }

    @Test
    fun `login with an unknown email fails as invalid credentials`() {
        assertFailsWith<InvalidCredentialsException> {
            loginAdmin.login(LoginAdmin.LoginAdminRequest("nobody@example.com", "whatever"))
        }
    }

    @Test
    fun `login with a wrong password fails as invalid credentials`() {
        registerAdmin.register(RegisterAdmin.RegisterAdminRequest("ada@example.com", "s3cret-pw"))

        assertFailsWith<InvalidCredentialsException> {
            loginAdmin.login(LoginAdmin.LoginAdminRequest("ada@example.com", "wrong-pw"))
        }
    }

    @Test
    fun `refresh rotates the token and invalidates the old one`() {
        registerAdmin.register(RegisterAdmin.RegisterAdminRequest("ada@example.com", "s3cret-pw"))
        val first = loginAdmin.login(LoginAdmin.LoginAdminRequest("ada@example.com", "s3cret-pw"))

        val second = refreshSession.refresh(first.token)

        assertNotEquals(first.token, second.token, "refresh issues a new token")
        assertFailsWith<UnauthenticatedException> { refreshSession.refresh(first.token) }
        // the rotated token still works
        assertEquals(second.email, refreshSession.refresh(second.token).email)
    }

    @Test
    fun `refresh rejects an expired session`() {
        registerAdmin.register(RegisterAdmin.RegisterAdminRequest("ada@example.com", "s3cret-pw"))
        val user = users.findByEmail("ada@example.com")!!
        val rawToken = "expired-token"
        sessions.save(
            AdminSession(
                id = SessionId("expired-session"),
                adminUserId = user.id,
                tokenHash = sessionTokens.hash(rawToken),
                createdAt = now.minus(Duration.ofDays(8)),
                expiresAt = now.minus(Duration.ofDays(1)),
            ),
        )

        assertFailsWith<UnauthenticatedException> { refreshSession.refresh(rawToken) }
    }

    @Test
    fun `refresh rejects an unknown session`() {
        assertFailsWith<UnauthenticatedException> { refreshSession.refresh("never-issued") }
    }

    @Test
    fun `logout invalidates the session`() {
        registerAdmin.register(RegisterAdmin.RegisterAdminRequest("ada@example.com", "s3cret-pw"))
        val issued = loginAdmin.login(LoginAdmin.LoginAdminRequest("ada@example.com", "s3cret-pw"))

        logoutAdmin.logout(issued.token)

        assertFailsWith<UnauthenticatedException> { refreshSession.refresh(issued.token) }
    }

    @Test
    fun `logout is idempotent for an unknown token`() {
        logoutAdmin.logout("never-issued")
    }
}
