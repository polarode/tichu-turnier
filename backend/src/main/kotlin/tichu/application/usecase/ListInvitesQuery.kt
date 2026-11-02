package tichu.application.usecase

import tichu.application.api.AdminInviteView
import tichu.application.api.AuthenticatedAdmin
import tichu.application.api.ListInvites
import tichu.application.api.toView
import tichu.domain.AdminInviteRepository

/**
 * A use case is a plain class implementing an inbound-port interface. It is wired
 * as a bean in [UseCaseConfiguration] — never annotated with @Service.
 */
class ListInvitesQuery(
    private val adminInvites: AdminInviteRepository,
) : ListInvites {

    override fun list(requester: AuthenticatedAdmin): List<AdminInviteView> {
        requester.requireOwner()
        return adminInvites.findAll().map { it.toView() }
    }
}
