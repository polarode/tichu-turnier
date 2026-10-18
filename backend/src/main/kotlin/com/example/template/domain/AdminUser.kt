package com.example.template.domain

import java.time.Instant

/**
 * A tournament organizer's account. Framework-free: the password is held only as an
 * opaque hash produced by the [PasswordHasher] port, never as plaintext.
 */
data class AdminUser(
    val id: AdminUserId,
    val email: String,
    val passwordHash: String,
    val createdAt: Instant,
)

/** A typed id keeps domain signatures explicit and avoids passing bare strings around. */
@JvmInline
value class AdminUserId(val value: String)

/** Outbound port: persistence for [AdminUser]. Implemented in `infra/persistence`. */
interface AdminUserRepository {

    fun save(user: AdminUser): AdminUser

    /** Emails are normalized to lowercase by the use cases, so lookup is exact. */
    fun findByEmail(email: String): AdminUser?

    fun findById(id: AdminUserId): AdminUser?
}
