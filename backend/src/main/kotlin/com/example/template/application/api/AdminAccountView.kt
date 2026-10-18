package com.example.template.application.api

import com.example.template.domain.AdminRole
import com.example.template.domain.AdminStatus
import com.example.template.domain.AdminUser
import com.example.template.domain.AdminUserId
import java.time.Instant

/**
 * Application-level read model of an admin account for the owner console. It is a
 * deliberate projection of [AdminUser]: there is no password hash field, so no listing
 * or mutation response can ever leak credential material — the type makes it impossible,
 * rather than relying on the web adapter remembering to drop it.
 */
data class AdminAccountView(
    val id: AdminUserId,
    val email: String,
    val status: AdminStatus,
    val role: AdminRole,
    val createdAt: Instant,
    val approvedBy: AdminUserId?,
    val approvedAt: Instant?,
    /** The owner who invited this account, when it was activated by an invite rather than an approval. */
    val invitedBy: AdminUserId?,
    /** When the invite was accepted, recorded together with [invitedBy]. */
    val invitedAt: Instant?,
)

/** The single projection point; every admin-management use case returns through it. */
fun AdminUser.toView(): AdminAccountView = AdminAccountView(
    id = id,
    email = email,
    status = status,
    role = role,
    createdAt = createdAt,
    approvedBy = approvedBy,
    approvedAt = approvedAt,
    invitedBy = invitedBy,
    invitedAt = invitedAt,
)
