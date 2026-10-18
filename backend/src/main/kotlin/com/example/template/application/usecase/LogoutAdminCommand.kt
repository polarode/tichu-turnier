package com.example.template.application.usecase

import com.example.template.application.api.LogoutAdmin
import com.example.template.domain.AdminSessionRepository
import com.example.template.domain.SessionTokens

/**
 * Deletes the session behind the presented token. Idempotent: a missing or already
 * deleted session is not an error, because logging out should always end in a clean,
 * unauthenticated state.
 */
class LogoutAdminCommand(
    private val sessions: AdminSessionRepository,
    private val sessionTokens: SessionTokens,
) : LogoutAdmin {

    override fun logout(rawToken: String) {
        sessions.deleteByTokenHash(sessionTokens.hash(rawToken))
    }
}
