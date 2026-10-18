package com.example.template.domain

import java.time.Instant

/**
 * An owner-issued, email-addressed invitation to become an admin. Framework-free: like
 * a session token, the invite's secret is held only as a hash produced by the
 * [SessionTokens] port, so the raw token exists only in the copyable link handed to the
 * owner at issuance.
 *
 * An invite is usable exactly once and only within its lifetime: [status] is
 * [InviteStatus.PENDING] until it is accepted ([InviteStatus.SPENT]) or revoked
 * ([InviteStatus.REVOKED]); a pending invite past [expiresAt] is dead even without a
 * status change.
 */
data class AdminInvite(
    val id: InviteId,
    val email: String,
    val tokenHash: String,
    val status: InviteStatus,
    /** The owner who issued it; copied onto the account as `invitedBy` when accepted. */
    val createdBy: AdminUserId,
    val createdAt: Instant,
    val expiresAt: Instant,
    /** Set when the invite is accepted, together with the account's `invitedAt`. */
    val spentAt: Instant? = null,
) {
    /** A pending invite is dead once [expiresAt] has passed, even before it is swept. */
    fun isExpired(now: Instant): Boolean = !expiresAt.isAfter(now)
}

/** A typed id keeps domain signatures explicit and avoids passing bare strings around. */
@JvmInline
value class InviteId(val value: String)

/**
 * Invite lifecycle. A fresh invite is [PENDING]; accepting it makes it [SPENT] and an
 * owner can [REVOKED] it. Both consuming states are terminal, so an invite is
 * single-use. Expiry is time-based and expressed through [AdminInvite.isExpired].
 */
enum class InviteStatus {
    PENDING,
    SPENT,
    REVOKED,
}

/** Outbound port: persistence for [AdminInvite]. Implemented in `infra/persistence`. */
interface AdminInviteRepository {

    fun save(invite: AdminInvite): AdminInvite

    /** Invite secrets are looked up by hash only; the raw token is never stored. */
    fun findByTokenHash(tokenHash: String): AdminInvite?

    fun findById(id: InviteId): AdminInvite?

    /** Every invite, for the owner console's list. Never carries a token. */
    fun findAll(): List<AdminInvite>
}
