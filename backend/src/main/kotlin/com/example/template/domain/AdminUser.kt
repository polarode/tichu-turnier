package com.example.template.domain

import java.time.Instant

/**
 * A tournament organizer's account. Framework-free: the password is held only as an
 * opaque hash produced by the [PasswordHasher] port, never as plaintext.
 *
 * Accounts follow a lifecycle ([status]); only an [AdminStatus.ACTIVE] account may log
 * in or hold a working session. [role] decides authority: an [AdminRole.OWNER] manages
 * other admins, an [AdminRole.ADMIN] only their own tournaments.
 */
data class AdminUser(
    val id: AdminUserId,
    val email: String,
    val passwordHash: String,
    val status: AdminStatus,
    val role: AdminRole,
    val createdAt: Instant,
    /** The owner who approved this account, recorded when a PENDING request is approved. */
    val approvedBy: AdminUserId? = null,
    /** When approval happened, recorded together with [approvedBy]. */
    val approvedAt: Instant? = null,
    /** The owner who issued the invite this account was activated with, when it was not approved. */
    val invitedBy: AdminUserId? = null,
    /** When the invite was accepted, recorded together with [invitedBy]. */
    val invitedAt: Instant? = null,
) {
    /** Only an [AdminStatus.ACTIVE] account may log in or authenticate a session. */
    fun isActive(): Boolean = status == AdminStatus.ACTIVE
}

/** A typed id keeps domain signatures explicit and avoids passing bare strings around. */
@JvmInline
value class AdminUserId(val value: String)

/**
 * Admin emails are matched case-insensitively and stored normalized, so every lookup
 * and write funnels through this one function — register, login and the owner
 * bootstrap can never drift apart.
 */
fun normalizeEmail(raw: String): String = raw.trim().lowercase()

/**
 * Account lifecycle. A registration starts [PENDING] and only an owner approval (or a
 * valid invite) makes it [ACTIVE]; [DISABLED] revokes access again. Every state other
 * than [ACTIVE] is refused at login and during session authentication.
 */
enum class AdminStatus {
    PENDING,
    ACTIVE,
    DISABLED,
}

/**
 * Authority level. [OWNER] is provisioned from configuration only — no endpoint can
 * create or promote to it — while [ADMIN] is what registration and invites produce.
 */
enum class AdminRole {
    OWNER,
    ADMIN,
}

/** Outbound port: persistence for [AdminUser]. Implemented in `infra/persistence`. */
interface AdminUserRepository {

    fun save(user: AdminUser): AdminUser

    /** Emails are normalized to lowercase by the use cases, so lookup is exact. */
    fun findByEmail(email: String): AdminUser?

    fun findById(id: AdminUserId): AdminUser?

    /** Every account, newest or oldest order unspecified. Used by the owner console. */
    fun findAll(): List<AdminUser>

    /** Only the accounts currently in [status], for the owner console's status tabs. */
    fun findAllByStatus(status: AdminStatus): List<AdminUser>

    /** Removes a rejected registration request. No-op for an unknown id. */
    fun deleteById(id: AdminUserId)
}
