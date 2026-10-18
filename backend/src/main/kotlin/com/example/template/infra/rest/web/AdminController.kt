package com.example.template.infra.rest.web

import com.example.template.application.api.ApproveAdmin
import com.example.template.application.api.AuthenticatedAdmin
import com.example.template.application.api.DisableAdmin
import com.example.template.application.api.ListAdmins
import com.example.template.application.api.ReactivateAdmin
import com.example.template.application.api.RejectAdmin
import com.example.template.domain.AdminUserId
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * Driving adapter for the owner's admin-management console. Depends only on the
 * inbound-port interfaces, injected by
 * [com.example.template.infra.rest.RestConfiguration].
 *
 * Every method forwards the authenticated principal; the use cases enforce the owner
 * role and raise the 403, so this adapter holds no authorization logic of its own and
 * a signed-in non-owner is refused even if it reaches the controller.
 */
@RestController
@RequestMapping("/api/admins")
class AdminController(
    private val listAdmins: ListAdmins,
    private val approveAdmin: ApproveAdmin,
    private val rejectAdmin: RejectAdmin,
    private val disableAdmin: DisableAdmin,
    private val reactivateAdmin: ReactivateAdmin,
) {

    @GetMapping
    fun list(
        @RequestParam(required = false) status: String?,
        @AuthenticationPrincipal principal: AuthenticatedAdmin,
    ): List<AdminAccountResponse> =
        listAdmins.list(principal, status).map(AdminAccountResponse::from)

    @PostMapping("/{id}/approve")
    fun approve(
        @PathVariable id: String,
        @AuthenticationPrincipal principal: AuthenticatedAdmin,
    ): AdminAccountResponse =
        AdminAccountResponse.from(approveAdmin.approve(principal, AdminUserId(id)))

    @PostMapping("/{id}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun reject(
        @PathVariable id: String,
        @AuthenticationPrincipal principal: AuthenticatedAdmin,
    ) {
        rejectAdmin.reject(principal, AdminUserId(id))
    }

    @PostMapping("/{id}/disable")
    fun disable(
        @PathVariable id: String,
        @AuthenticationPrincipal principal: AuthenticatedAdmin,
    ): AdminAccountResponse =
        AdminAccountResponse.from(disableAdmin.disable(principal, AdminUserId(id)))

    @PostMapping("/{id}/reactivate")
    fun reactivate(
        @PathVariable id: String,
        @AuthenticationPrincipal principal: AuthenticatedAdmin,
    ): AdminAccountResponse =
        AdminAccountResponse.from(reactivateAdmin.reactivate(principal, AdminUserId(id)))
}
