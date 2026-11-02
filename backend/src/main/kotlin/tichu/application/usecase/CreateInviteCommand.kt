package tichu.application.usecase

import tichu.application.api.AuthenticatedAdmin
import tichu.application.api.CreateInvite
import tichu.application.api.IssuedInvite
import tichu.application.api.toView
import tichu.domain.AdminInvite
import tichu.domain.AdminInviteRepository
import tichu.domain.IdGenerator
import tichu.domain.InviteId
import tichu.domain.InviteStatus
import tichu.domain.SessionTokens
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration

/**
 * A use case is a plain class implementing an inbound-port interface. It is wired
 * as a bean in [UseCaseConfiguration] — never annotated with @Service.
 *
 * Issuance generates a high-entropy token through the [SessionTokens] port and persists
 * only its hash, returning the raw token once.
 */
@Transactional
class CreateInviteCommand(
    private val adminInvites: AdminInviteRepository,
    private val sessionTokens: SessionTokens,
    private val idGenerator: IdGenerator,
    private val clock: Clock,
    private val inviteTtl: Duration,
) : CreateInvite {

    override fun create(requester: AuthenticatedAdmin, request: CreateInvite.CreateInviteRequest): IssuedInvite {
        requester.requireOwner()
        val now = clock.instant()
        val rawToken = sessionTokens.generate()
        val invite = adminInvites.save(
            AdminInvite(
                id = InviteId(idGenerator.newId()),
                label = request.label.trim(),
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
