package com.example.template.application.api

import com.example.template.domain.AdminUser

/** Inbound port (driving side). Controllers depend on this, never on the implementation. */
interface RegisterAdmin {
    fun register(command: RegisterAdminRequest): AdminUser

    data class RegisterAdminRequest(
        val email: String,
        val password: String,
    )
}
