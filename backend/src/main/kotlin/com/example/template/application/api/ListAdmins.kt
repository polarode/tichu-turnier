package com.example.template.application.api

/**
 * Inbound port (driving side). Owner-only: the implementation enforces the requester's
 * role, so a non-owner admin is refused with a 403 `FORBIDDEN` problem rather than
 * reaching any data.
 */
interface ListAdmins {
    /**
     * Lists accounts in the status named by [status] (case-insensitive), or every account
     * when [status] is null. The owner check runs before the filter is parsed, so a
     * non-owner always gets its 403 regardless of the query shape.
     */
    fun list(requester: AuthenticatedAdmin, status: String?): List<AdminAccountView>
}
