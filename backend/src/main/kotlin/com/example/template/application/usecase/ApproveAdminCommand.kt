package com.example.template.application.usecase

import com.example.template.application.api.AdminAccountView
import com.example.template.application.api.ApproveAdmin
import com.example.template.application.api.AuthenticatedAdmin
import com.example.template.application.api.toView
import com.example.template.domain.AdminStatus
import com.example.template.domain.AdminUserId
import com.example.template.domain.AdminUserRepository
import com.example.template.vocabulary.AdminStatusConflictException
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * A use case is a plain class implementing an inbound-port interface. It is wired
 * as a bean in [UseCaseConfiguration] — never annotated with @Service.
 *
 * Approval is the only way a `PENDING` registration becomes usable, and it stamps the
 * approver and the time onto the account so the audit trail survives later edits.
 */
@Transactional
class ApproveAdminCommand(
    private val adminUsers: AdminUserRepository,
    private val clock: Clock,
) : ApproveAdmin {

    override fun approve(requester: AuthenticatedAdmin, target: AdminUserId): AdminAccountView {
        requester.requireOwner()
        val user = adminUsers.requireById(target)
        if (user.status != AdminStatus.PENDING) {
            throw AdminStatusConflictException("Only a pending registration request can be approved")
        }
        val approved = user.copy(
            status = AdminStatus.ACTIVE,
            approvedBy = requester.adminUserId,
            approvedAt = clock.instant(),
        )
        return adminUsers.save(approved).toView()
    }
}
