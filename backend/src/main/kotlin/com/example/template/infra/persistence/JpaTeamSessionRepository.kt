package com.example.template.infra.persistence

import com.example.template.domain.TeamSession
import com.example.template.domain.TeamSessionRepository

/**
 * Driven adapter implementing [TeamSessionRepository] on top of Spring Data JPA and
 * PostgreSQL. Deletion is expressed as find-then-delete so it participates in whatever
 * transaction the calling use case has opened (or runs in the repository's own).
 */
class JpaTeamSessionRepository(
    private val springData: SpringDataTeamSessionRepository,
) : TeamSessionRepository {

    override fun save(session: TeamSession): TeamSession =
        springData.save(TeamSessionEntity.from(session)).toDomain()

    override fun findByTokenHash(tokenHash: String): TeamSession? =
        springData.findByTokenHash(tokenHash)?.toDomain()

    override fun deleteByTokenHash(tokenHash: String) {
        springData.findByTokenHash(tokenHash)?.let { springData.delete(it) }
    }
}