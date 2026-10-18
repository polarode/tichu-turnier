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
}
