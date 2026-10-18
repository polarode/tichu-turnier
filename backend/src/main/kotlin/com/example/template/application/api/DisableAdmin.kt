package com.example.template.application.api

import com.example.template.domain.AdminUserId

/**
 * Inbound port (driving side). Owner-only: disables an `ACTIVE` admin. The change is
 * read back on every request, so a live session is refused on its very next call.
 */
interface DisableAdmin {
    fun disable(requester: AuthenticatedAdmin, target: AdminUserId): AdminAccountView
}
