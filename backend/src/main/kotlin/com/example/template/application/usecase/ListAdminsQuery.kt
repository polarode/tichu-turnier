package com.example.template.application.usecase

import com.example.template.application.api.AdminAccountView
import com.example.template.application.api.AuthenticatedAdmin
import com.example.template.application.api.ListAdmins
import com.example.template.application.api.toView
import com.example.template.domain.AdminStatus
import com.example.template.domain.AdminUserRepository
import com.example.template.vocabulary.InvalidFieldException

/**
 * A use case is a plain class implementing an inbound-port interface. It is wired
 * as a bean in [UseCaseConfiguration] — never annotated with @Service.
 */
class ListAdminsQuery(
    private val adminUsers: AdminUserRepository,
) : ListAdmins {

    override fun list(requester: AuthenticatedAdmin, status: String?): List<AdminAccountView> {
        // The role check precedes parsing the filter: the endpoint is owner-only, so a
        // non-owner is refused before its query is even looked at.
        requester.requireOwner()
        val filter = status?.let(::parseStatus)
        val accounts = filter?.let { adminUsers.findAllByStatus(it) } ?: adminUsers.findAll()
        return accounts.map { it.toView() }
    }

    /** Lenient on case, strict on the vocabulary, field-anchored like other bad input. */
    private fun parseStatus(raw: String): AdminStatus =
        AdminStatus.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
            ?: throw InvalidFieldException("status", "Unknown admin status: $raw")
}
