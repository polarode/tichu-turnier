package com.example.template.infra.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional

/** Spring Data repository for [TeamSessionEntity]. Implementation detail of the JPA adapter. */
interface SpringDataTeamSessionRepository : JpaRepository<TeamSessionEntity, String> {
    fun findByTokenHash(tokenHash: String): TeamSessionEntity?

    /** Bulk delete so a rotation/revocation cannot trip a stale-entity check. */
    @Modifying
    @Query("DELETE FROM TeamSessionEntity s WHERE s.teamId = :teamId")
    fun deleteByTeamId(@Param("teamId") teamId: String)

    /** Bulk delete of expired rows; idempotent, safe to run concurrently. */
    @Transactional
    @Modifying
    @Query("DELETE FROM TeamSessionEntity s WHERE s.expiresAt < :now")
    fun deleteExpiredBefore(@Param("now") now: java.time.Instant)
}