package com.example.template.application.usecase

import com.example.template.application.api.AuthenticatedAdmin
import com.example.template.domain.AdminRole
import com.example.template.domain.AdminStatus
import com.example.template.domain.AdminUser
import com.example.template.domain.AdminUserId
import com.example.template.testsupport.InMemoryAdminUserRepository
import com.example.template.vocabulary.AdminStatusConflictException
import com.example.template.vocabulary.ForbiddenException
import com.example.template.vocabulary.InvalidFieldException
import com.example.template.vocabulary.NotFoundException
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pure unit test: no Spring context. The admin-management use cases are the seam where
 * the owner role and the account lifecycle are enforced, so the matrix is exercised here
 * against in-memory ports; the HTTP contract (401 vs 403, no credential leakage) is
 * covered by the integration test.
 */
class AdminManagementUseCasesTest {

    private val now = Instant.parse("2026-02-01T09:00:00Z")
    private val fixedClock = Clock.fixed(now, ZoneOffset.UTC)
    private val users = InMemoryAdminUserRepository()

    private val listAdmins = ListAdminsQuery(users)
    private val approveAdmin = ApproveAdminCommand(users, fixedClock)
    private val rejectAdmin = RejectAdminCommand(users)
    private val disableAdmin = DisableAdminCommand(users)
    private val reactivateAdmin = ReactivateAdminCommand(users)

    private var sequence = 0
    private fun nextId(): AdminUserId = AdminUserId("id-${++sequence}")

    private fun seed(
        email: String,
        status: AdminStatus = AdminStatus.ACTIVE,
        role: AdminRole = AdminRole.ADMIN,
    ): AdminUser = users.save(
        AdminUser(
            id = nextId(),
            email = email,
            passwordHash = "hashed-password",
            status = status,
            role = role,
            createdAt = now,
        ),
    )

    private fun owner() = AuthenticatedAdmin(AdminUserId("owner-id"), "owner@example.com", AdminRole.OWNER)

    private fun plainAdmin() = AuthenticatedAdmin(AdminUserId("admin-id"), "admin@example.com", AdminRole.ADMIN)

    @Test
    fun `owner lists every account when no status filter is given`() {
        seed("pending@example.com", status = AdminStatus.PENDING)
        seed("active@example.com")

        val accounts = listAdmins.list(owner(), status = null)

        assertEquals(setOf("pending@example.com", "active@example.com"), accounts.map { it.email }.toSet())
    }

    @Test
    fun `listing filters by status`() {
        seed("pending@example.com", status = AdminStatus.PENDING)
        seed("active@example.com")
        seed("disabled@example.com", status = AdminStatus.DISABLED)

        val pending = listAdmins.list(owner(), status = "pending")

        assertEquals(listOf("pending@example.com"), pending.map { it.email })
        assertTrue(pending.all { it.status == AdminStatus.PENDING })
    }

    @Test
    fun `the listing view carries no credential material`() {
        seed("active@example.com")

        val account = listAdmins.list(owner(), status = null).single()

        assertEquals("active@example.com", account.email)
        assertEquals(AdminStatus.ACTIVE, account.status)
        assertEquals(AdminRole.ADMIN, account.role)
        assertEquals(now, account.createdAt)
        assertNull(account.approvedBy)
        assertNull(account.approvedAt)
    }

    @Test
    fun `an unknown status filter is a field-anchored invalid input`() {
        assertFailsWith<InvalidFieldException> { listAdmins.list(owner(), status = "NOPE") }
    }

    @Test
    fun `a non-owner cannot list admins`() {
        assertFailsWith<ForbiddenException> { listAdmins.list(plainAdmin(), status = null) }
    }

    @Test
    fun `a non-owner is refused before the status filter is parsed`() {
        // Role enforcement must win over input validation, so the caller cannot probe
        // the endpoint's query handling while unprivileged.
        assertFailsWith<ForbiddenException> { listAdmins.list(plainAdmin(), status = "NOPE") }
    }

    @Test
    fun `approving a pending request activates it and records the approver and timestamp`() {
        val pending = seed("pending@example.com", status = AdminStatus.PENDING)

        val approved = approveAdmin.approve(owner(), pending.id)

        assertEquals(AdminStatus.ACTIVE, approved.status)
        assertEquals(owner().adminUserId, approved.approvedBy)
        assertEquals(now, approved.approvedAt)
        assertEquals(AdminStatus.ACTIVE, users.findById(pending.id)!!.status, "the change is persisted")
    }

    @Test
    fun `approving an account that is not pending is a status conflict`() {
        val active = seed("active@example.com")

        assertFailsWith<AdminStatusConflictException> { approveAdmin.approve(owner(), active.id) }
    }

    @Test
    fun `approving an unknown account reports not found`() {
        assertFailsWith<NotFoundException> { approveAdmin.approve(owner(), AdminUserId("missing")) }
    }

    @Test
    fun `rejecting a pending request removes it`() {
        val pending = seed("pending@example.com", status = AdminStatus.PENDING)

        rejectAdmin.reject(owner(), pending.id)

        assertNull(users.findById(pending.id), "a rejected request leaves no account behind")
    }

    @Test
    fun `rejecting an account that is not pending is a status conflict`() {
        val active = seed("active@example.com")

        assertFailsWith<AdminStatusConflictException> { rejectAdmin.reject(owner(), active.id) }
    }

    @Test
    fun `disabling an active admin marks it disabled`() {
        val active = seed("active@example.com")

        val disabled = disableAdmin.disable(owner(), active.id)

        assertEquals(AdminStatus.DISABLED, disabled.status)
        assertEquals(AdminStatus.DISABLED, users.findById(active.id)!!.status)
    }

    @Test
    fun `disabling the owner account is refused`() {
        val ownerAccount = seed("owner@example.com", role = AdminRole.OWNER)

        assertFailsWith<ForbiddenException> { disableAdmin.disable(owner(), ownerAccount.id) }
        assertEquals(AdminStatus.ACTIVE, users.findById(ownerAccount.id)!!.status)
    }

    @Test
    fun `disabling an account that is not active is a status conflict`() {
        val pending = seed("pending@example.com", status = AdminStatus.PENDING)

        assertFailsWith<AdminStatusConflictException> { disableAdmin.disable(owner(), pending.id) }
    }

    @Test
    fun `reactivating a disabled admin restores it to active`() {
        val disabled = seed("disabled@example.com", status = AdminStatus.DISABLED)

        val reactivated = reactivateAdmin.reactivate(owner(), disabled.id)

        assertEquals(AdminStatus.ACTIVE, reactivated.status)
        assertEquals(AdminStatus.ACTIVE, users.findById(disabled.id)!!.status)
    }

    @Test
    fun `reactivating an account that is not disabled is a status conflict`() {
        val active = seed("active@example.com")

        assertFailsWith<AdminStatusConflictException> { reactivateAdmin.reactivate(owner(), active.id) }
    }

    @Test
    fun `every mutation refuses a non-owner`() {
        val pending = seed("pending@example.com", status = AdminStatus.PENDING)
        val active = seed("active@example.com")
        val disabled = seed("disabled@example.com", status = AdminStatus.DISABLED)

        assertFailsWith<ForbiddenException> { approveAdmin.approve(plainAdmin(), pending.id) }
        assertFailsWith<ForbiddenException> { rejectAdmin.reject(plainAdmin(), pending.id) }
        assertFailsWith<ForbiddenException> { disableAdmin.disable(plainAdmin(), active.id) }
        assertFailsWith<ForbiddenException> { reactivateAdmin.reactivate(plainAdmin(), disabled.id) }

        // None of the refusals changed anything.
        assertEquals(AdminStatus.PENDING, users.findById(pending.id)!!.status)
        assertEquals(AdminStatus.ACTIVE, users.findById(active.id)!!.status)
        assertEquals(AdminStatus.DISABLED, users.findById(disabled.id)!!.status)
    }
}
