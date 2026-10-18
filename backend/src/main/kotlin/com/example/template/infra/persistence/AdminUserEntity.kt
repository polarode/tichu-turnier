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
) {
    fun toDomain(): AdminUser = AdminUser(
        id = AdminUserId(id),
        email = email,
        passwordHash = passwordHash,
        status = AdminStatus.valueOf(status),
        role = AdminRole.valueOf(role),
        createdAt = createdAt,
    )

    companion object {
        fun from(user: AdminUser): AdminUserEntity = AdminUserEntity(
            id = user.id.value,
            email = user.email,
            passwordHash = user.passwordHash,
            status = user.status.name,
            role = user.role.name,
            createdAt = user.createdAt,
        )
    }
}
