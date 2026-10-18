package com.example.template.application.api

/**
 * Inbound port (driving side). Exchanges a still-valid session token for a freshly
 * issued one, rotating the old token out of existence.
 */
interface RefreshSession {
    fun refresh(rawToken: String): IssuedSession
}
