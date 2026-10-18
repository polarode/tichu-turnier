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
 * create when absent, promote in place when present, no-op when unconfigured.
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
    fun `promotes an existing account with the configured email instead of duplicating it`() {
        val existing = users.save(
            AdminUser(
                id = AdminUserId("existing"),
                email = "owner@example.com",
                passwordHash = "existing-hash",
                status = AdminStatus.ACTIVE,
                role = AdminRole.ADMIN,
                createdAt = now,
            ),
        )

        val owner = bootstrap().ensureOwner()!!

        assertEquals(existing.id, owner.id)
        assertEquals(AdminRole.OWNER, owner.role)
        assertEquals("existing-hash", owner.passwordHash, "an existing account's password is left untouched")
        assertEquals(1, users.findAll().size)
    }

    @Test
    fun `is a no-op when no owner is configured`() {
        assertNull(bootstrap(email = "", hash = "hashed-owner-pw").ensureOwner())
        assertNull(bootstrap(email = "owner@example.com", hash = "   ").ensureOwner())

        assertEquals(0, users.findAll().size)
    }
}
