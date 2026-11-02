package tichu.application.usecase

import tichu.application.api.AdminAccountView
import tichu.application.api.AuthenticatedAdmin
import tichu.application.api.ReactivateAdmin
import tichu.application.api.toView
import tichu.domain.AdminStatus
import tichu.domain.AdminUserId
import tichu.domain.AdminUserRepository
import tichu.vocabulary.AdminStatusConflictException
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
