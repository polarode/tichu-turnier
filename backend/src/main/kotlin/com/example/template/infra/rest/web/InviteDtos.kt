package com.example.template.infra.rest.web

import com.example.template.application.api.AdminInviteView
import com.example.template.application.api.IssuedInvite
import com.example.template.domain.InviteStatus
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Wire DTO for issuing an invite. Validated at the boundary; carries no secret. */
data class CreateInviteRequestBody(
    @field:NotBlank @field:Email val email: String,
)

/**
 * The one response that carries the raw token — returned exactly once, at issuance.
 * [link] is the copyable, ready-to-share URL the owner hands out; it embeds the same
 * token, so either field can be used by a client.
 */
data class CreateInviteResponse(
    val id: String,
    val email: String,
    val status: InviteStatus,
    val createdAt: Instant,
    val expiresAt: Instant,
    val token: String,
    val link: String,
) {
    companion object {
        fun from(issued: IssuedInvite, frontendUrl: String): CreateInviteResponse {
            val invite = issued.invite
            val encodedToken = URLEncoder.encode(issued.token, StandardCharsets.UTF_8)
            return CreateInviteResponse(
                id = invite.id.value,
                email = invite.email,
                status = invite.status,
                createdAt = invite.createdAt,
                expiresAt = invite.expiresAt,
                token = issued.token,
                link = "${frontendUrl.trimEnd('/')}/register?invite=$encodedToken",
            )
        }
    }
}

/**
 * Public wire view of an invite. Built from the application's [AdminInviteView], it has
 * no token or token-hash field by construction, so no listing can leak invite material.
 */
data class InviteResponse(
    val id: String,
    val email: String,
    val status: InviteStatus,
    val createdBy: String,
    val createdAt: Instant,
    val expiresAt: Instant,
    val spentAt: Instant?,
) {
    companion object {
        fun from(view: AdminInviteView): InviteResponse = InviteResponse(
            id = view.id.value,
            email = view.email,
            status = view.status,
            createdBy = view.createdBy.value,
            createdAt = view.createdAt,
            expiresAt = view.expiresAt,
            spentAt = view.spentAt,
        )
    }
}
