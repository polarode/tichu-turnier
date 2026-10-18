package com.example.template.application.usecase

import com.example.template.domain.AdminRole
import com.example.template.domain.AdminStatus
import com.example.template.domain.AdminUser
import com.example.template.domain.AdminUserId
import com.example.template.domain.IdGenerator
import com.example.template.testsupport.InMemoryAdminUserRepository
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pure unit test: no Spring context. Covers the owner bootstrap's convergence rules —
 * create when absent, converge a genuine owner, refuse a foreign account that merely
 * holds the configured email, and no-op when unconfigured.
 */
class BootstrapOwnerCommandTest {

    private val now = Instant.parse("2026-01-01T12:00:00Z")
    private val fixedClock = Clock.fixed(now, ZoneOffset.UTC)
    private val users = InMemoryAdminUserRepository()

    private val idGenerator = object : IdGenerator {
        private var counter = 0
        override fun newId(): String = "owner-${++counter}"
    }

    private fun bootstrap(email: String = "owner@example.com", hash: String = "hashed-owner-pw") =
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
        assertEquals("hashed-owner-pw", owner.passwordHash, "the configured hash is stored verbatim")
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
        val existing = storedUser(id = "owner", passwordHash = "old-hash", role = AdminRole.OWNER)

        val owner = bootstrap(email = "Owner@Example.com", hash = "new-hash").ensureOwner()!!

        assertEquals(existing.id, owner.id)
        assertEquals(AdminRole.OWNER, owner.role)
        assertEquals("new-hash", owner.passwordHash, "rotating the configured hash updates the owner")
        assertEquals(1, users.findAll().size)
    }

    @Test
    fun `leaves an existing owner's lifecycle state untouched`() {
        storedUser(id = "owner", passwordHash = "old-hash", status = AdminStatus.DISABLED, role = AdminRole.OWNER)

        val owner = bootstrap().ensureOwner()!!

        assertEquals(AdminStatus.DISABLED, owner.status, "a re-run must not re-activate a disabled owner")
    }

    @Test
    fun `refuses to claim a non-owner account holding the configured email`() {
        storedUser(id = "squatter", passwordHash = "someone-elses-hash", status = AdminStatus.PENDING)

        assertNull(bootstrap().ensureOwner())

        val untouched = users.findByEmail("owner@example.com")!!
        assertEquals(AdminRole.ADMIN, untouched.role, "a foreign account must never be promoted")
        assertEquals(AdminStatus.PENDING, untouched.status)
        assertEquals("someone-elses-hash", untouched.passwordHash, "a foreign password must never be overwritten")
    }

    @Test
    fun `is a no-op when no owner is configured`() {
        assertNull(bootstrap(email = "", hash = "hashed-owner-pw").ensureOwner())
        assertNull(bootstrap(email = "owner@example.com", hash = "   ").ensureOwner())

        assertEquals(0, users.findAll().size)
    }
}
