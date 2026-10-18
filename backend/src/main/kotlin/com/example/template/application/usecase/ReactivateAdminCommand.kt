package com.example.template.application.usecase

import com.example.template.application.api.AdminAccountView
import com.example.template.application.api.AuthenticatedAdmin
import com.example.template.application.api.ReactivateAdmin
import com.example.template.application.api.toView
import com.example.template.domain.AdminStatus
import com.example.template.domain.AdminUserId
import com.example.template.domain.AdminUserRepository
import com.example.template.vocabulary.AdminStatusConflictException
import org.springframework.transaction.annotation.Transactional

/**
 * A use case is a plain class implementing an inbound-port interface. It is wired
 * as a bean in [UseCaseConfiguration] — never annotated with @Service.
 *
 * Reactivation is the inverse of disabling: a disabled admin becomes `ACTIVE` again and
 * can log in, but any session it held before disabling stays dead.
 */
@Transactional
class ReactivateAdminCommand(
    private val adminUsers: AdminUserRepository,
) : ReactivateAdmin {

    override fun reactivate(requester: AuthenticatedAdmin, target: AdminUserId): AdminAccountView {
        requester.requireOwner()
        val user = adminUsers.requireById(target)
        if (user.status != AdminStatus.DISABLED) {
            throw AdminStatusConflictException("Only a disabled admin can be reactivated")
        }
        return adminUsers.save(user.copy(status = AdminStatus.ACTIVE)).toView()
    }
}
