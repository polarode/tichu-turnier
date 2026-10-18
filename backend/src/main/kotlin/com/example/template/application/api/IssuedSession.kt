package com.example.template.application.api

import com.example.template.domain.AdminUserId
import java.time.Instant

/**
 * The result of a successful login or refresh. [token] is the raw opaque token that
 * the web adapter puts into the session cookie — it is never returned in a response
 * body and never logged.
 */
data class IssuedSession(
    val adminUserId: AdminUserId,
    val email: String,
    val token: String,
    val expiresAt: Instant,
)
