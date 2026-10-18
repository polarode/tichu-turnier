package com.example.template.infra.persistence

import com.example.template.domain.AdminRole
import com.example.template.domain.AdminStatus
import com.example.template.domain.AdminUser
import com.example.template.domain.AdminUserId
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * Persistence model for [AdminUser]. Lives in the infra layer — the domain entity
 * stays framework-free. Status and role change over an account's life, so `save`
 * merges updates rather than only inserting.
 */
@Entity
@Table(name = "admin_users")
class AdminUserEntity(
    @Id
    val id: String,
    val email: String,
    val passwordHash: String,
    val status: String,
    val role: String,
    val createdAt: Instant,
    /** Null until a pending request is approved; then the approving owner's id. */
    val approvedBy: String?,
    /** Null until approval; recorded together with [approvedBy]. */
    val approvedAt: Instant?,
    /** Null unless the account was activated by an invite; then the issuing owner's id. */
    val invitedBy: String?,
    /** Null unless the account was activated by an invite; recorded together with [invitedBy]. */
    val invitedAt: Instant?,
) {
    fun toDomain(): AdminUser = AdminUser(
        id = AdminUserId(id),
        email = email,
        passwordHash = passwordHash,
        status = AdminStatus.valueOf(status),
        role = AdminRole.valueOf(role),
        createdAt = createdAt,
        approvedBy = approvedBy?.let(::AdminUserId),
        approvedAt = approvedAt,
        invitedBy = invitedBy?.let(::AdminUserId),
        invitedAt = invitedAt,
    )

    companion object {
        fun from(user: AdminUser): AdminUserEntity = AdminUserEntity(
            id = user.id.value,
            email = user.email,
            passwordHash = user.passwordHash,
            status = user.status.name,
            role = user.role.name,
            createdAt = user.createdAt,
            approvedBy = user.approvedBy?.value,
            approvedAt = user.approvedAt,
            invitedBy = user.invitedBy?.value,
            invitedAt = user.invitedAt,
        )
    }
}
