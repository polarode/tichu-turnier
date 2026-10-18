package com.example.template.application.api

import com.example.template.domain.AdminInvite
import com.example.template.domain.AdminUserId
import com.example.template.domain.InviteId
import com.example.template.domain.InviteStatus
import java.time.Instant

/**
 * Application-level read model of an admin invite for the owner console. It is a
 * deliberate projection of [AdminInvite]: there is no token or token-hash field, so no
 * listing or mutation response can ever leak invite material — the type makes it
 * impossible, rather than relying on the web adapter remembering to drop it.
 */
data class AdminInviteView(
    val id: InviteId,
    val email: String,
    val status: InviteStatus,
    val createdBy: AdminUserId,
    val createdAt: Instant,
    val expiresAt: Instant,
    val spentAt: Instant?,
)

/** The single projection point; every invite use case returns through it. */
fun AdminInvite.toView(): AdminInviteView = AdminInviteView(
    id = id,
    email = email,
    status = status,
    createdBy = createdBy,
    createdAt = createdAt,
    expiresAt = expiresAt,
    spentAt = spentAt,
)

/**
 * The one response that carries the secret: the newly issued invite plus its raw token.
 * It is returned exactly once, by [CreateInvite], and there is no read path that can
 * reproduce it — only the hash is persisted.
 */
data class IssuedInvite(
    val invite: AdminInviteView,
    val token: String,
)
