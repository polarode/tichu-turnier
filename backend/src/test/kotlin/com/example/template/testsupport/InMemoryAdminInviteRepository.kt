package com.example.template.testsupport

import com.example.template.domain.AdminInvite
import com.example.template.domain.AdminInviteRepository
import com.example.template.domain.InviteId

/** In-memory double for [AdminInviteRepository], used by pure use-case tests. */
class InMemoryAdminInviteRepository : AdminInviteRepository {

    private val invites = LinkedHashMap<String, AdminInvite>()

    override fun save(invite: AdminInvite): AdminInvite {
        invites[invite.id.value] = invite
        return invite
    }

    override fun findByTokenHash(tokenHash: String): AdminInvite? =
        invites.values.firstOrNull { it.tokenHash == tokenHash }

    override fun findById(id: InviteId): AdminInvite? = invites[id.value]

    override fun findAll(): List<AdminInvite> = invites.values.toList()
}
