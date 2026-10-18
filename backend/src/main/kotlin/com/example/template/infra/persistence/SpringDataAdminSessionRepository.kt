package com.example.template.infra.persistence

import org.springframework.data.jpa.repository.JpaRepository

/**
 * Spring Data repository for [AdminSessionEntity]. Implementation detail of the JPA
 * adapter; the application-facing port is
 * [com.example.template.domain.AdminSessionRepository].
 */
interface SpringDataAdminSessionRepository : JpaRepository<AdminSessionEntity, String> {
    fun findByTokenHash(tokenHash: String): AdminSessionEntity?
}
