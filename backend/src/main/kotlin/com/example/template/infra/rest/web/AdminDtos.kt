package com.example.template.infra.rest.web

import com.example.template.application.api.AdminAccountView
import com.example.template.domain.AdminRole
import com.example.template.domain.AdminStatus
import java.time.Instant

/**
 * Public wire view of an admin account for the owner console. It is built from the
 * application's [AdminAccountView], so it has no password hash or token field by
 * construction and no listing can leak credential material.
 */
data class AdminAccountResponse(
    val id: String,
    val email: String,
    val status: AdminStatus,
    val role: AdminRole,
    val createdAt: Instant,
    val approvedBy: String?,
    val approvedAt: Instant?,
) {
    companion object {
        fun from(view: AdminAccountView): AdminAccountResponse = AdminAccountResponse(
            id = view.id.value,
            email = view.email,
            status = view.status,
            role = view.role,
            createdAt = view.createdAt,
            approvedBy = view.approvedBy?.value,
            approvedAt = view.approvedAt,
        )
    }
}
