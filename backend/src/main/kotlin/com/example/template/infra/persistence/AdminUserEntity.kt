package com.example.template.infra.persistence

import com.example.template.domain.AdminUser
import com.example.template.domain.AdminUserId
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * Persistence model for [AdminUser]. Lives in the infra layer — the domain entity
 * stays framework-free. Admin accounts are never updated after creation, so all
 * fields are immutable from the adapter's point of view.
 */
@Entity
@Table(name = "admin_users")
class AdminUserEntity(
    @Id
    val id: String,
    val email: String,
    val passwordHash: String,
    val createdAt: Instant,
) {
    fun toDomain(): AdminUser = AdminUser(
        id = AdminUserId(id),
        email = email,
        passwordHash = passwordHash,
        createdAt = createdAt,
    )

    companion object {
        fun from(user: AdminUser): AdminUserEntity = AdminUserEntity(
            id = user.id.value,
            email = user.email,
            passwordHash = user.passwordHash,
            createdAt = user.createdAt,
        )
    }
}
