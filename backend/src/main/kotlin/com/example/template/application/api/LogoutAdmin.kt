package com.example.template.application.api

/**
 * Inbound port (driving side). Invalidates the session identified by [logout].
 * Idempotent: logging out an unknown or already-invalidated token does nothing.
 */
interface LogoutAdmin {
    fun logout(rawToken: String)
}
