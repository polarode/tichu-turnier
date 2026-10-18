package com.example.template.application.usecase

import com.example.template.application.api.RegisterAdmin
import com.example.template.domain.AdminInvite
import com.example.template.domain.AdminInviteRepository
import com.example.template.domain.AdminRole
import com.example.template.domain.AdminStatus
import com.example.template.domain.AdminUser
import com.example.template.domain.AdminUserId
import com.example.template.domain.AdminUserRepository
import com.example.template.domain.IdGenerator
import com.example.template.domain.InviteStatus
import com.example.template.domain.PasswordHasher
import com.example.template.domain.SessionTokens
import com.example.template.domain.normalizeEmail
import com.example.template.vocabulary.InviteEmailMismatchException
import com.example.template.vocabulary.InviteExpiredException
import com.example.template.vocabulary.InviteInvalidException
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
        val invite = command.inviteToken?.let { acceptInvite(it, email, now) }

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
     * spent or revoked token all fail identically; expiry and email mismatch are checked
     * only afterwards, so guessing a token (or using a stale one) reveals nothing about
     * the invite's address.
     */
    private fun acceptInvite(rawToken: String, email: String, now: Instant): AdminInvite {
        val invite = adminInvites.findByTokenHash(sessionTokens.hash(rawToken))
            ?: throw InviteInvalidException("This invite is not valid")
        if (invite.status != InviteStatus.PENDING) {
            throw InviteInvalidException("This invite is no longer valid")
        }
        if (invite.isExpired(now)) {
            throw InviteExpiredException("This invite has expired")
        }
        if (invite.email != email) {
            throw InviteEmailMismatchException("This invite was issued for a different email address")
        }
        return invite
    }
}
