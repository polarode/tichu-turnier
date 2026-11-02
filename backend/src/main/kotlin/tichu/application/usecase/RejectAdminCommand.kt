package tichu.application.usecase

import tichu.application.api.AuthenticatedAdmin
import tichu.application.api.RejectAdmin
import tichu.domain.AdminStatus
import tichu.domain.AdminUserId
import tichu.domain.AdminUserRepository
import tichu.vocabulary.AdminStatusConflictException
import org.springframework.transaction.annotation.Transactional

/**
 * A use case is a plain class implementing an inbound-port interface. It is wired
 * as a bean in [UseCaseConfiguration] — never annotated with @Service.
 *
 * Rejection removes the request outright: there is no half-approved account left behind,
 * and the email is free to register again.
 */
@Transactional
class RejectAdminCommand(
    private val adminUsers: AdminUserRepository,
) : RejectAdmin {

    override fun reject(requester: AuthenticatedAdmin, target: AdminUserId) {
        requester.requireOwner()
        val user = adminUsers.requireById(target)
        if (user.status != AdminStatus.PENDING) {
            throw AdminStatusConflictException("Only a pending registration request can be rejected")
        }
        adminUsers.deleteById(target)
    }
}
