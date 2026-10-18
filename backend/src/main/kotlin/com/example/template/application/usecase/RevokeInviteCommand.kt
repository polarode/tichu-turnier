package com.example.template.application.usecase

import com.example.template.application.api.AdminInviteView
import com.example.template.application.api.AuthenticatedAdmin
import com.example.template.application.api.RevokeInvite
import com.example.template.application.api.toView
import com.example.template.domain.AdminInviteRepository
import com.example.template.domain.InviteId
import com.example.template.domain.InviteStatus
import com.example.template.vocabulary.InviteStatusConflictException
import com.example.template.vocabulary.NotFoundException
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
