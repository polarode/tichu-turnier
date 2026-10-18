package com.example.template.infra.persistence

import com.example.template.domain.AdminSession
import com.example.template.domain.AdminSessionRepository
import java.time.Instant

/**
 * Driven adapter implementing [AdminSessionRepository] on top of Spring Data JPA and
 * PostgreSQL. Deletion is expressed as find-then-delete so it participates in whatever
 * transaction the calling use case has opened (or runs in the repository's own).
 */
class JpaAdminSessionRepository(
    private val springData: SpringDataAdminSessionRepository,
) : AdminSessionRepository {

    override fun save(session: AdminSession): AdminSession =
        springData.save(AdminSessionEntity.from(session)).toDomain()

    override fun findByTokenHash(tokenHash: String): AdminSession? =
        springData.findByTokenHash(tokenHash)?.toDomain()

    override fun deleteByTokenHash(tokenHash: String) {
        springData.findByTokenHash(tokenHash)?.let { springData.delete(it) }
    }

    override fun deleteExpiredBefore(now: Instant) {
        springData.deleteExpiredBefore(now)
    }
}
