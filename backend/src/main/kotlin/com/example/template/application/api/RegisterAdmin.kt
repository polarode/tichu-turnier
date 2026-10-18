package com.example.template.application.api

import com.example.template.domain.AdminUser

/** Inbound port (driving side). Controllers depend on this, never on the implementation. */
interface RegisterAdmin {
    /**
     * Creates an account. Without an invite the account starts `PENDING` and needs an
     * owner approval; with a valid [RegisterAdminRequest.inviteToken] for the same email
     * it starts `ACTIVE` and consumes the invite.
     */
    fun register(command: RegisterAdminRequest): AdminUser

    data class RegisterAdminRequest(
        val email: String,
        val password: String,
        /** The raw invite token from a copyable link, when registration follows an invite. */
        val inviteToken: String? = null,
    )
}
