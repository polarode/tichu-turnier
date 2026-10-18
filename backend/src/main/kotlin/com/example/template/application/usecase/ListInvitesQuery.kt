package com.example.template.application.usecase

import com.example.template.application.api.AdminInviteView
import com.example.template.application.api.AuthenticatedAdmin
import com.example.template.application.api.ListInvites
import com.example.template.application.api.toView
import com.example.template.domain.AdminInviteRepository

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
