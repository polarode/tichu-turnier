package com.example.template.infra.rest.web

import com.example.template.application.api.AuthenticatedAdmin
import com.example.template.application.api.CreateInvite
import com.example.template.application.api.ListInvites
import com.example.template.application.api.RevokeInvite
import com.example.template.domain.InviteId
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * Driving adapter for owner-issued admin invites. Depends only on the inbound-port
 * interfaces, injected by [com.example.template.infra.rest.RestConfiguration].
 *
 * Every method forwards the authenticated principal; the use cases enforce the owner
 * role and raise the 403, so this adapter holds no authorization logic of its own. Only
 * issuance returns token material, and only once — it is never persisted or listed.
 */
@RestController
@RequestMapping("/api/invites")
class InviteController(
    private val createInvite: CreateInvite,
    private val listInvites: ListInvites,
    private val revokeInvite: RevokeInvite,
    private val frontendUrl: String,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid @RequestBody body: CreateInviteRequestBody,
        @AuthenticationPrincipal principal: AuthenticatedAdmin,
    ): CreateInviteResponse {
        val issued = createInvite.create(principal, CreateInvite.CreateInviteRequest(email = body.email))
        return CreateInviteResponse.from(issued, frontendUrl)
    }

    @GetMapping
    fun list(
        @AuthenticationPrincipal principal: AuthenticatedAdmin,
    ): List<InviteResponse> = listInvites.list(principal).map(InviteResponse::from)

    @PostMapping("/{id}/revoke")
    fun revoke(
        @PathVariable id: String,
        @AuthenticationPrincipal principal: AuthenticatedAdmin,
    ): InviteResponse =
        InviteResponse.from(revokeInvite.revoke(principal, InviteId(id)))
}
