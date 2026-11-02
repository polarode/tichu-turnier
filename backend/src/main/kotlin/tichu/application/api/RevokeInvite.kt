package tichu.application.api

import tichu.domain.InviteId

/**
 * Inbound port (driving side). Owner-only: revokes a pending invite so its link stops
 * working immediately. A spent or already-revoked invite is a status conflict.
 */
interface RevokeInvite {
    fun revoke(requester: AuthenticatedAdmin, inviteId: InviteId): AdminInviteView
}
