package com.example.template.application.api

import com.example.template.domain.AdminUserId

/**
 * Inbound port (driving side). Read-only counterpart to [RefreshSession]: it resolves
 * a raw session token to the admin it belongs to without rotating anything, so the
 * security filter chain can authenticate every request. Throws
 * `UnauthenticatedException` for a missing, expired or unknown session.
 */
interface AuthenticateSession {
    fun authenticate(rawToken: String): AuthenticatedAdmin
}

/** The identity established by a valid session: enough to authorize, and to scope data. */
data class AuthenticatedAdmin(
    val adminUserId: AdminUserId,
    val email: String,
)
