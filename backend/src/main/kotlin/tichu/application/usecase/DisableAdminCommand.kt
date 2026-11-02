package tichu.application.usecase

import tichu.application.api.AdminAccountView
import tichu.application.api.AuthenticatedAdmin
import tichu.application.api.DisableAdmin
import tichu.application.api.toView
import tichu.domain.AdminRole
import tichu.domain.AdminStatus
import tichu.domain.AdminUserId
import tichu.domain.AdminUserRepository
import tichu.vocabulary.AdminStatusConflictException
import tichu.vocabulary.ForbiddenException
import org.springframework.transaction.annotation.Transactional

/**
 * A use case is a plain class implementing an inbound-port interface. It is wired
 * as a bean in [UseCaseConfiguration] — never annotated with @Service.
 *
 * The owner account itself is deliberately not disableable: there is exactly one owner,
 * provisioned from configuration, and disabling it would lock the platform with no
 * recovery path. Sessions re-read status on every request, so a disabled admin's live
 * session dies on its next call.
 */
@Transactional
class DisableAdminCommand(
    private val adminUsers: AdminUserRepository,
) : DisableAdmin {

    override fun disable(requester: AuthenticatedAdmin, target: AdminUserId): AdminAccountView {
        requester.requireOwner()
        val user = adminUsers.requireById(target)
        if (user.role == AdminRole.OWNER) {
            throw ForbiddenException("The platform owner account cannot be disabled")
        }
        if (user.status != AdminStatus.ACTIVE) {
            throw AdminStatusConflictException("Only an active admin can be disabled")
        }
        return adminUsers.save(user.copy(status = AdminStatus.DISABLED)).toView()
    }
}
