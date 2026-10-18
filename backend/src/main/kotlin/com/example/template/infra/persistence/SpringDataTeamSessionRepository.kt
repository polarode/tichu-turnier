package com.example.template.infra.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/** Spring Data repository for [TeamSessionEntity]. Implementation detail of the JPA adapter. */
interface SpringDataTeamSessionRepository : JpaRepository<TeamSessionEntity, String> {
    fun findByTokenHash(tokenHash: String): TeamSessionEntity?

    /** Bulk delete so a rotation/revocation cannot trip a stale-entity check. */
    @Modifying
    @Query("DELETE FROM TeamSessionEntity s WHERE s.teamId = :teamId")
    fun deleteByTeamId(@Param("teamId") teamId: String)
}