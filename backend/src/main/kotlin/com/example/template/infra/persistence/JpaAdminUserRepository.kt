package com.example.template.infra.persistence

import com.example.template.domain.AdminUser
import com.example.template.domain.AdminUserId
import com.example.template.domain.AdminUserRepository

/**
 * Driven adapter implementing [AdminUserRepository] on top of Spring Data JPA and
 * PostgreSQL (schema managed by Flyway).
 */
class JpaAdminUserRepository(
    private val springData: SpringDataAdminUserRepository,
) : AdminUserRepository {

    override fun save(user: AdminUser): AdminUser = springData.save(AdminUserEntity.from(user)).toDomain()

    override fun findByEmail(email: String): AdminUser? =
        springData.findByEmail(email)?.toDomain()

    override fun findById(id: AdminUserId): AdminUser? =
        springData.findById(id.value).map { it.toDomain() }.orElse(null)
}
