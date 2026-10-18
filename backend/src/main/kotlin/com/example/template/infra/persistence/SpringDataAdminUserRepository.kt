package com.example.template.infra.persistence

import org.springframework.data.jpa.repository.JpaRepository

/**
 * Spring Data repository for [AdminUserEntity]. Implementation detail of the JPA
 * adapter; the application-facing port is
 * [com.example.template.domain.AdminUserRepository].
 */
interface SpringDataAdminUserRepository : JpaRepository<AdminUserEntity, String> {
    fun findByEmail(email: String): AdminUserEntity?

    fun findByStatus(status: String): List<AdminUserEntity>
}
