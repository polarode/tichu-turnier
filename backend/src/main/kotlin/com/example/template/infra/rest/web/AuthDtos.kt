package com.example.template.infra.rest.web

import com.example.template.domain.AdminRole
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/** Wire DTOs for the auth endpoints. Validated at the boundary; never carry token material. */
data class RegisterAdminRequestBody(
    @field:NotBlank @field:Email val email: String,
    @field:NotBlank @field:Size(min = 8, max = 100) val password: String,
    /** Raw token from an invite link; when present and valid the account activates immediately. */
    val inviteToken: String? = null,
)

data class LoginRequestBody(
    @field:NotBlank @field:Email val email: String,
    @field:NotBlank val password: String,
)

/** Public view of an account. Deliberately has no password or token fields. */
data class AdminResponse(
    val id: String,
    val email: String,
    /** The account's role, so the client can gate an owner-only console. */
    val role: AdminRole,
)
