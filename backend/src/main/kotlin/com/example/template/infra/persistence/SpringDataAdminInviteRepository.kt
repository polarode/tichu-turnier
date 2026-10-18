package com.example.template.infra.persistence

import org.springframework.data.jpa.repository.JpaRepository

/**
 * Spring Data repository for [AdminInviteEntity]. Implementation detail of the JPA
 * adapter; the application-facing port is
 * [com.example.template.domain.AdminInviteRepository].
 */
interface SpringDataAdminInviteRepository : JpaRepository<AdminInviteEntity, String> {
    fun findByTokenHash(tokenHash: String): AdminInviteEntity?
}
