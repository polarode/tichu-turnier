package com.example.template.infra.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional

/**
 * Spring Data repository for [AdminSessionEntity]. Implementation detail of the JPA
 * adapter; the application-facing port is
 * [com.example.template.domain.AdminSessionRepository].
 */
interface SpringDataAdminSessionRepository : JpaRepository<AdminSessionEntity, String> {
    fun findByTokenHash(tokenHash: String): AdminSessionEntity?

    /** Bulk delete of expired rows; idempotent, safe to run concurrently. */
    @Transactional
    @Modifying
    @Query("DELETE FROM AdminSessionEntity s WHERE s.expiresAt < :now")
    fun deleteExpiredBefore(@Param("now") now: java.time.Instant)
}
