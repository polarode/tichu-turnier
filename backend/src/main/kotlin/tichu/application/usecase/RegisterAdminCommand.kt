package tichu.application.usecase

import tichu.application.api.RegisterAdmin
import tichu.domain.AdminInvite
import tichu.domain.AdminInviteRepository
import tichu.domain.AdminRole
import tichu.domain.AdminStatus
import tichu.domain.AdminUser
import tichu.domain.AdminUserId
import tichu.domain.AdminUserRepository
import tichu.domain.IdGenerator
import tichu.domain.InviteStatus
import tichu.domain.PasswordHasher
import tichu.domain.SessionTokens
import tichu.domain.normalizeEmail
import tichu.vocabulary.InviteExpiredException
import tichu.vocabulary.InviteInvalidException
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

/**
 * A use case is a plain class implementing an inbound-port interface. It is wired
 * as a bean in [UseCaseConfiguration] — never annotated with @Service.
 *
 * Registration has two paths. Without an invite the account starts `PENDING` and waits
 * for an owner. With an invite token the account is activated immediately and the invite
 * is consumed in the same transaction, so a token can never be spent twice and an
 * accepted account can never exist alongside a still-pending invite.
 */
@Transactional
class RegisterAdminCommand(
    private val adminUsers: AdminUserRepository,
    private val adminInvites: AdminInviteRepository,
    private val passwordHasher: PasswordHasher,
    private val sessionTokens: SessionTokens,
    private val idGenerator: IdGenerator,
    private val clock: Clock,
) : RegisterAdmin {

    override fun register(command: RegisterAdmin.RegisterAdminRequest): AdminUser {
        val email = normalizeEmail(command.email)
        adminUsers.requireEmailFree(email)

        val now = clock.instant()
        val invite = command.inviteToken?.let { acceptInvite(it, now) }

        val user = AdminUser(
            id = AdminUserId(idGenerator.newId()),
            email = email,
            passwordHash = passwordHasher.hash(command.password),
            status = if (invite != null) AdminStatus.ACTIVE else AdminStatus.PENDING,
            role = AdminRole.ADMIN,
            createdAt = now,
            invitedBy = invite?.createdBy,
            invitedAt = invite?.createdAt,
        )
        val saved = adminUsers.save(user)
        invite?.let { adminInvites.save(it.copy(status = InviteStatus.SPENT, spentAt = now)) }
        return saved
    }

    /**
     * Validates the presented token and returns the pending invite it names. An unknown,
     * spent or revoked token all fail identically; expiry is checked only afterwards. The
     * invite is not bound to an email, so whoever holds the link chooses the address.
     */
    private fun acceptInvite(rawToken: String, now: Instant): AdminInvite {
        val invite = adminInvites.findByTokenHash(sessionTokens.hash(rawToken))
            ?: throw InviteInvalidException("This invite is not valid")
        if (invite.status != InviteStatus.PENDING) {
            throw InviteInvalidException("This invite is no longer valid")
        }
        if (invite.isExpired(now)) {
            throw InviteExpiredException("This invite has expired")
        }
        return invite
    }
}
