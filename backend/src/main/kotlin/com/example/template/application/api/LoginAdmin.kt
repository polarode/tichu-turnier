package com.example.template.application.api

/** Inbound port (driving side). Controllers depend on this, never on the implementation. */
interface LoginAdmin {
    fun login(command: LoginAdminRequest): IssuedSession

    data class LoginAdminRequest(
        val email: String,
        val password: String,
    )
}
