package com.example.template.application.api

import com.example.template.domain.AdminUserId

/**
 * Inbound port (driving side). Owner-only: restores a `DISABLED` admin to `ACTIVE`,
 * which restores login.
 */
interface ReactivateAdmin {
    fun reactivate(requester: AuthenticatedAdmin, target: AdminUserId): AdminAccountView
}
