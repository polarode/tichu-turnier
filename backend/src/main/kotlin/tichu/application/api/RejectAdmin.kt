package tichu.application.api

import tichu.domain.AdminUserId

/**
 * Inbound port (driving side). Owner-only: rejects a `PENDING` registration request by
 * removing the account entirely, so it can never log in.
 */
interface RejectAdmin {
    fun reject(requester: AuthenticatedAdmin, target: AdminUserId)
}
