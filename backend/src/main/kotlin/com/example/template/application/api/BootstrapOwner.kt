package com.example.template.application.api

import com.example.template.domain.AdminUser

/**
 * Inbound port (driving side). Idempotently provisions the platform owner from
 * configuration so a fresh deployment has exactly one way in. There is deliberately no
 * HTTP endpoint behind this: an owner is never created or promoted by a request.
 */
interface BootstrapOwner {

    /**
     * Creates the configured owner if absent, otherwise converges an existing account
     * with that email to `OWNER`/`ACTIVE`. Returns the owner, or null when no owner is
     * configured.
     */
    fun ensureOwner(): AdminUser?
}
