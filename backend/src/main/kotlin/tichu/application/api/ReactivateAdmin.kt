package tichu.application.api

import tichu.domain.AdminUserId

/**
 * Inbound port (driving side). Owner-only: restores a `DISABLED` admin to `ACTIVE`,
 * which restores login.
 */
interface ReactivateAdmin {
    fun reactivate(requester: AuthenticatedAdmin, target: AdminUserId): AdminAccountView
}
