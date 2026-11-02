package tichu.application.usecase

import tichu.application.api.AuthenticatedAdmin
import tichu.application.api.CreateInvite
import tichu.application.api.RegisterAdmin
import tichu.domain.AdminInvite
import tichu.domain.AdminRole
import tichu.domain.AdminStatus
import tichu.domain.AdminUser
import tichu.domain.AdminUserId
import tichu.domain.IdGenerator
import tichu.domain.InviteId
import tichu.domain.InviteStatus
import tichu.domain.normalizeEmail
import tichu.infra.security.BcryptPasswordHasher
import tichu.infra.security.SecureSessionTokens
import tichu.testsupport.InMemoryAdminInviteRepository
import tichu.testsupport.InMemoryAdminUserRepository
import tichu.vocabulary.EmailAlreadyRegisteredException
import tichu.vocabulary.ForbiddenException
import tichu.vocabulary.InviteExpiredException
import tichu.vocabulary.InviteInvalidException
import tichu.vocabulary.InviteStatusConflictException
import tichu.vocabulary.NotFoundException
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pure unit test: no Spring context. The invite use cases are the seam where the owner
 * role, the invite lifecycle and the invite→account audit are enforced, so the matrix is
 * exercised here against in-memory ports. Token entropy is checked against the real
 * [SecureSessionTokens] adapter, so "high-entropy and stored hashed" is not simulated.
 */
class InviteUseCasesTest {

    private val now = Instant.parse("2026-02-01T09:00:00Z")
    private val fixedClock = Clock.fixed(now, ZoneOffset.UTC)
    private val inviteTtl = Duration.ofDays(7)
    private val users = InMemoryAdminUserRepository()
    private val invites = InMemoryAdminInviteRepository()
    private val passwordHasher = BcryptPasswordHasher()
    private val sessionTokens = SecureSessionTokens()

    private val idGenerator = object : IdGenerator {
        private var counter = 0
        override fun newId(): String = "id-${++counter}"
    }

    private val createInvite = CreateInviteCommand(invites, sessionTokens, idGenerator, fixedClock, inviteTtl)
    private val listInvites = ListInvitesQuery(invites)
    private val revokeInvite = RevokeInviteCommand(invites)
    private val registerAdmin =
        RegisterAdminCommand(users, invites, passwordHasher, sessionTokens, idGenerator, fixedClock)

    private fun owner() = AuthenticatedAdmin(AdminUserId("owner-id"), "owner@example.com", AdminRole.OWNER)

    private fun plainAdmin() = AuthenticatedAdmin(AdminUserId("admin-id"), "admin@example.com", AdminRole.ADMIN)

    private fun issue(label: String = "Ada") =
        createInvite.create(owner(), CreateInvite.CreateInviteRequest(label))

    private fun seedUser(email: String, status: AdminStatus = AdminStatus.ACTIVE): AdminUser =
        users.save(
            AdminUser(
                id = AdminUserId(idGenerator.newId()),
                email = normalizeEmail(email),
                passwordHash = "hashed-password",
                status = status,
                role = AdminRole.ADMIN,
                createdAt = now,
            ),
        )

    private fun seedInvite(
        rawToken: String,
        label: String = "Ada",
        status: InviteStatus = InviteStatus.PENDING,
        expiresAt: Instant = now.plus(inviteTtl),
        createdAt: Instant = now,
    ): AdminInvite = invites.save(
        AdminInvite(
            id = InviteId(idGenerator.newId()),
            label = label,
            tokenHash = sessionTokens.hash(rawToken),
            status = status,
            createdBy = owner().adminUserId,
            createdAt = createdAt,
            expiresAt = expiresAt,
            spentAt = if (status == InviteStatus.SPENT) now else null,
        ),
    )

    @Test
    fun `issuing an invite returns the raw token once and stores only its high-entropy hash`() {
        val issued = issue("  Ada Lovelace ")

        assertEquals("Ada Lovelace", issued.invite.label, "the invite label is trimmed")
        assertEquals(InviteStatus.PENDING, issued.invite.status)
        assertEquals(owner().adminUserId, issued.invite.createdBy)
        assertEquals(now, issued.invite.createdAt)
        assertEquals(now.plus(inviteTtl), issued.invite.expiresAt)
        assertNull(issued.invite.spentAt)

        // 256 bits of entropy encode to 43 URL-safe base64 characters.
        assertTrue(issued.token.length >= 43, "the invite token is high-entropy, got '${issued.token}'")
        val stored = invites.findByTokenHash(sessionTokens.hash(issued.token))!!
        assertNotEquals(issued.token, stored.tokenHash, "only the hash is stored, never the raw token")
        assertEquals(sessionTokens.hash(issued.token), stored.tokenHash)
    }

    @Test
    fun `an invite is not tied to an email address`() {
        seedUser("taken@example.com")

        // The label only tells the owner who the link is for; it is not checked anywhere.
        val issued = issue("taken@example.com")

        assertEquals(InviteStatus.PENDING, issued.invite.status)
    }

    @Test
    fun `a non-owner cannot issue an invite`() {
        assertFailsWith<ForbiddenException> {
            createInvite.create(plainAdmin(), CreateInvite.CreateInviteRequest("Ada"))
        }
    }

    @Test
    fun `listing invites never carries the token`() {
        val issued = issue()

        val listed = listInvites.list(owner()).single()

        assertEquals(issued.invite.id, listed.id)
        assertEquals(issued.invite.label, listed.label)
        assertEquals(InviteStatus.PENDING, listed.status)
        assertEquals(owner().adminUserId, listed.createdBy)
    }

    @Test
    fun `a non-owner cannot list invites`() {
        issue()

        assertFailsWith<ForbiddenException> { listInvites.list(plainAdmin()) }
    }

    @Test
    fun `revoking a pending invite marks it revoked`() {
        val issued = issue()

        val revoked = revokeInvite.revoke(owner(), issued.invite.id)

        assertEquals(InviteStatus.REVOKED, revoked.status)
        assertEquals(InviteStatus.REVOKED, invites.findById(issued.invite.id)!!.status)
    }

    @Test
    fun `revoking an unknown invite reports not found`() {
        assertFailsWith<NotFoundException> { revokeInvite.revoke(owner(), InviteId("missing")) }
    }

    @Test
    fun `revoking an invite that is not pending is a status conflict`() {
        val spent = seedInvite("raw-token", status = InviteStatus.SPENT)

        assertFailsWith<InviteStatusConflictException> { revokeInvite.revoke(owner(), spent.id) }
    }

    @Test
    fun `a non-owner cannot revoke an invite`() {
        val issued = issue()

        assertFailsWith<ForbiddenException> { revokeInvite.revoke(plainAdmin(), issued.invite.id) }
        assertEquals(InviteStatus.PENDING, invites.findById(issued.invite.id)!!.status)
    }

    @Test
    fun `registering with a valid invite activates immediately and records the inviter`() {
        val issued = issue()

        val account = registerAdmin.register(
            RegisterAdmin.RegisterAdminRequest("invitee@example.com", "s3cret-pw", issued.token),
        )

        assertEquals(AdminStatus.ACTIVE, account.status, "an invited account skips the pending state")
        assertEquals(owner().adminUserId, account.invitedBy)
        assertEquals(now, account.invitedAt)
        assertNull(account.approvedBy, "an invited account is not owner-approved")
        assertEquals(AdminStatus.ACTIVE, users.findById(account.id)!!.status, "the activation is persisted")

        val stored = invites.findById(issued.invite.id)!!
        assertEquals(InviteStatus.SPENT, stored.status, "the invite is consumed")
        assertEquals(now, stored.spentAt)
    }

    @Test
    fun `an invite cannot be used twice`() {
        val issued = issue()
        registerAdmin.register(RegisterAdmin.RegisterAdminRequest("first@example.com", "s3cret-pw", issued.token))

        // A different email skips the duplicate-account check, isolating the invite's single use.
        assertFailsWith<InviteInvalidException> {
            registerAdmin.register(RegisterAdmin.RegisterAdminRequest("second@example.com", "s3cret-pw", issued.token))
        }
    }

    @Test
    fun `the account records when the invite was issued, not just when it was accepted`() {
        val issuedAt = now.minus(Duration.ofDays(2))
        seedInvite("early-token", createdAt = issuedAt, expiresAt = now.plus(inviteTtl))

        val account = registerAdmin.register(
            RegisterAdmin.RegisterAdminRequest("invitee@example.com", "s3cret-pw", "early-token"),
        )

        assertEquals(now, account.createdAt, "the account is created at acceptance")
        assertEquals(issuedAt, account.invitedAt, "the audit records the invitation time")
    }

    @Test
    fun `an expired invite is rejected`() {
        seedInvite("stale-token", expiresAt = now.minus(Duration.ofMinutes(1)))

        assertFailsWith<InviteExpiredException> {
            registerAdmin.register(RegisterAdmin.RegisterAdminRequest("invitee@example.com", "s3cret-pw", "stale-token"))
        }
    }

    @Test
    fun `a revoked invite is rejected`() {
        seedInvite("revoked-token", status = InviteStatus.REVOKED)

        assertFailsWith<InviteInvalidException> {
            registerAdmin.register(
                RegisterAdmin.RegisterAdminRequest("invitee@example.com", "s3cret-pw", "revoked-token"),
            )
        }
    }

    @Test
    fun `an invite can be redeemed with any email address`() {
        val issued = issue("Ada")

        val account = registerAdmin.register(
            RegisterAdmin.RegisterAdminRequest("whoever@example.com", "s3cret-pw", issued.token),
        )

        assertEquals("whoever@example.com", account.email)
        assertEquals(AdminStatus.ACTIVE, account.status)
    }

    @Test
    fun `redeeming an invite with an already registered email is refused and leaves the invite pending`() {
        seedUser("taken@example.com")
        val issued = issue()

        assertFailsWith<EmailAlreadyRegisteredException> {
            registerAdmin.register(RegisterAdmin.RegisterAdminRequest("taken@example.com", "s3cret-pw", issued.token))
        }
        assertEquals(InviteStatus.PENDING, invites.findById(issued.invite.id)!!.status)
    }

    @Test
    fun `an unknown invite token is rejected`() {
        assertFailsWith<InviteInvalidException> {
            registerAdmin.register(
                RegisterAdmin.RegisterAdminRequest("invitee@example.com", "s3cret-pw", "never-issued"),
            )
        }
        assertTrue(users.findByEmail("invitee@example.com") == null, "no account is created on rejection")
    }
}
