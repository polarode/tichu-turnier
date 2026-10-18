package com.example.template.application.usecase

import com.example.template.application.api.AuthenticatedAdmin
import com.example.template.application.api.CreateInvite
import com.example.template.application.api.IssuedInvite
import com.example.template.application.api.toView
import com.example.template.domain.AdminInvite
import com.example.template.domain.AdminInviteRepository
import com.example.template.domain.AdminUserRepository
import com.example.template.domain.IdGenerator
import com.example.template.domain.InviteId
import com.example.template.domain.InviteStatus
import com.example.template.domain.SessionTokens
import com.example.template.domain.normalizeEmail
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration

/**
 * A use case is a plain class implementing an inbound-port interface. It is wired
 * as a bean in [UseCaseConfiguration] — never annotated with @Service.
 *
 * Issuance generates a high-entropy token through the [SessionTokens] port and persists
 * only its hash, returning the raw token once. The email is normalized here so the
 * acceptance check compares normalized values on both sides.
 */
@Transactional
class CreateInviteCommand(
    private val adminInvites: AdminInviteRepository,
    private val adminUsers: AdminUserRepository,
    private val sessionTokens: SessionTokens,
    private val idGenerator: IdGenerator,
    private val clock: Clock,
    private val inviteTtl: Duration,
) : CreateInvite {

    override fun create(requester: AuthenticatedAdmin, request: CreateInvite.CreateInviteRequest): IssuedInvite {
        requester.requireOwner()
        val email = normalizeEmail(request.email)
        // An invite for an existing account could never be redeemed (registration would
        // refuse the duplicate email), so refuse to issue a dead link up front.
        adminUsers.requireEmailFree(email)

        val now = clock.instant()
        val rawToken = sessionTokens.generate()
        val invite = adminInvites.save(
            AdminInvite(
                id = InviteId(idGenerator.newId()),
                email = email,
                tokenHash = sessionTokens.hash(rawToken),
                status = InviteStatus.PENDING,
                createdBy = requester.adminUserId,
                createdAt = now,
                expiresAt = now.plus(inviteTtl),
            ),
        )
        return IssuedInvite(invite = invite.toView(), token = rawToken)
    }
}
