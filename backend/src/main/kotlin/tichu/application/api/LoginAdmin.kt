package tichu.application.api

/** Inbound port (driving side). Controllers depend on this, never on the implementation. */
interface LoginAdmin {
    fun login(command: LoginAdminRequest): IssuedSession

    /**
     * [sourceIp] feeds the per-IP limit on failed logins. `null` means the caller has no
     * network source (non-web callers) and is never limited.
     */
    data class LoginAdminRequest(
        val email: String,
        val password: String,
        val sourceIp: String? = null,
    )
}
