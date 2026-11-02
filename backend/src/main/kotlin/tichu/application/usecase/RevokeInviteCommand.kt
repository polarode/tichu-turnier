package tichu.application.usecase

import tichu.application.api.AdminInviteView
import tichu.application.api.AuthenticatedAdmin
import tichu.application.api.RevokeInvite
import tichu.application.api.toView
import tichu.domain.AdminInviteRepository
import tichu.domain.InviteId
import tichu.domain.InviteStatus
import tichu.vocabulary.InviteStatusConflictException
import tichu.vocabulary.NotFoundException
import org.springframework.transaction.annotation.Transactional

/**
 * A use case is a plain class implementing an inbound-port interface. It is wired
 * as a bean in [UseCaseConfiguration] — never annotated with @Service.
 *
 * Revocation is terminal: the link stops working on the next registration attempt and
 * cannot be un-revoked. Only a pending invite can be revoked.
 */
@Transactional
class RevokeInviteCommand(
    private val adminInvites: AdminInviteRepository,
) : RevokeInvite {

    override fun revoke(requester: AuthenticatedAdmin, inviteId: InviteId): AdminInviteView {
        requester.requireOwner()
        val invite = adminInvites.findById(inviteId)
            ?: throw NotFoundException("Invite not found")
        if (invite.status != InviteStatus.PENDING) {
            throw InviteStatusConflictException("Only a pending invite can be revoked")
        }
        return adminInvites.save(invite.copy(status = InviteStatus.REVOKED)).toView()
    }
}
