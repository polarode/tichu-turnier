package tichu.application.usecase

import tichu.domain.AdminRole
import tichu.domain.AdminStatus
import tichu.domain.AdminUser
import tichu.domain.AdminUserId
import tichu.domain.IdGenerator
import tichu.testsupport.InMemoryAdminUserRepository
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pure unit test: no Spring context. Covers the owner bootstrap's convergence rules —
 * create when absent, converge a genuine owner, refuse a foreign account that merely
 * holds the configured email, refuse a malformed (non-bcrypt) hash without touching the
 * repository, and no-op when unconfigured.
 */
class BootstrapOwnerCommandTest {

    private val now = Instant.parse("2026-01-01T12:00:00Z")
    private val fixedClock = Clock.fixed(now, ZoneOffset.UTC)
    private val users = InMemoryAdminUserRepository()

    private val idGenerator = object : IdGenerator {
        private var counter = 0
        override fun newId(): String = "owner-${++counter}"
    }

    private fun bootstrap(email: String = "owner@example.com", hash: String = OWNER_HASH) =
        BootstrapOwnerCommand(users, idGenerator, fixedClock, email, hash)

    private fun storedUser(
        id: String,
        passwordHash: String,
        status: AdminStatus = AdminStatus.ACTIVE,
        role: AdminRole = AdminRole.ADMIN,
    ) = users.save(
        AdminUser(
            id = AdminUserId(id),
            email = "owner@example.com",
            passwordHash = passwordHash,
            status = status,
            role = role,
            createdAt = now,
        ),
    )

    @Test
    fun `provisions an active owner from configuration`() {
        val owner = bootstrap().ensureOwner()!!

        assertEquals("owner@example.com", owner.email)
        assertEquals(AdminStatus.ACTIVE, owner.status)
        assertEquals(AdminRole.OWNER, owner.role)
        assertEquals(OWNER_HASH, owner.passwordHash, "the configured hash is stored verbatim")
        assertEquals(now, owner.createdAt)
    }

    @Test
    fun `running again is idempotent and keeps the same single owner`() {
        val first = bootstrap().ensureOwner()!!
        val second = bootstrap().ensureOwner()!!

        assertEquals(first.id, second.id)
        assertEquals(1, users.findAll().size, "a re-run must not create a second owner")
    }

    @Test
    fun `converges an existing owner's credential from configuration`() {
        val existing = storedUser(id = "owner", passwordHash = OWNER_HASH, role = AdminRole.OWNER)

        val owner = bootstrap(email = "Owner@Example.com", hash = ROTATED_HASH).ensureOwner()!!

        assertEquals(existing.id, owner.id)
        assertEquals(AdminRole.OWNER, owner.role)
        assertEquals(ROTATED_HASH, owner.passwordHash, "rotating the configured hash updates the owner")
        assertEquals(1, users.findAll().size)
    }

    @Test
    fun `leaves an existing owner's lifecycle state untouched`() {
        storedUser(id = "owner", passwordHash = OWNER_HASH, status = AdminStatus.DISABLED, role = AdminRole.OWNER)

        val owner = bootstrap().ensureOwner()!!

        assertEquals(AdminStatus.DISABLED, owner.status, "a re-run must not re-activate a disabled owner")
    }

    @Test
    fun `refuses to claim a non-owner account holding the configured email`() {
        storedUser(id = "squatter", passwordHash = SQUATTER_HASH, status = AdminStatus.PENDING)

        assertNull(bootstrap().ensureOwner())

        val untouched = users.findByEmail("owner@example.com")!!
        assertEquals(AdminRole.ADMIN, untouched.role, "a foreign account must never be promoted")
        assertEquals(AdminStatus.PENDING, untouched.status)
        assertEquals(SQUATTER_HASH, untouched.passwordHash, "a foreign password must never be overwritten")
    }

    @Test
    fun `refuses a truncated hash and keeps the existing owner's credential`() {
        storedUser(id = "owner", passwordHash = OWNER_HASH, role = AdminRole.OWNER)

        assertFailsWith<IllegalStateException> {
            bootstrap(hash = TRUNCATED_HASH).ensureOwner()
        }

        val owner = users.findByEmail("owner@example.com")!!
        assertEquals(OWNER_HASH, owner.passwordHash, "a malformed hash must never overwrite a working credential")
    }

    @Test
    fun `refuses a malformed hash on an empty repository without creating an owner`() {
        assertFailsWith<IllegalStateException> {
            bootstrap(hash = TRUNCATED_HASH).ensureOwner()
        }

        assertEquals(0, users.findAll().size, "no owner may be created from a malformed hash")
    }

    @Test
    fun `the refusal names the setting but never echoes the configured value`() {
        val error = assertFailsWith<IllegalStateException> {
            bootstrap(hash = TRUNCATED_HASH).ensureOwner()
        }

        val message = error.message.orEmpty()
        assertTrue(message.contains("OWNER_PASSWORD_HASH"), "message should name the env variable: $message")
        assertTrue(message.contains("app.auth.owner.password-hash"), "message should name the property: $message")
        assertTrue(message.contains("bcrypt"), "message should say it must be a bcrypt hash: $message")
        assertTrue(!message.contains(TRUNCATED_HASH), "message must never contain the configured value: $message")
    }

    @Test
    fun `is a no-op when no owner is configured`() {
        assertNull(bootstrap(email = "", hash = OWNER_HASH).ensureOwner())
        assertNull(bootstrap(email = "owner@example.com", hash = "   ").ensureOwner())

        assertEquals(0, users.findAll().size)
    }

    private companion object {
        /** A well-formed bcrypt hash: `$2a$` + two-digit cost + 53 characters (60 in total). */
        val OWNER_HASH = "\$2a\$10\$" + "a".repeat(53)
        val ROTATED_HASH = "\$2b\$12\$" + "B".repeat(53)
        val SQUATTER_HASH = "\$2a\$10\$" + "s".repeat(53)

        /** What a deployment tool leaves behind when it interpolates the `$` characters of a real hash. */
        const val TRUNCATED_HASH = "\$2a\$12"
    }
}
