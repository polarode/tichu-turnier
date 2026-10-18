package com.example.template.infra.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/** Spring Data repository for [TeamTokenEntity]. Implementation detail of the JPA adapter. */
interface SpringDataTeamTokenRepository : JpaRepository<TeamTokenEntity, String> {
    fun findByTeamId(teamId: String): TeamTokenEntity?

    fun findByTokenHash(tokenHash: String): TeamTokenEntity?

    /**
     * Bulk delete by team id. Deliberately not a derived entity delete: the schema
     * cascades team deletion onto this table, so a find-then-remove after the team
     * row is gone would touch zero rows and trip Hibernate's stale-object check.
     * A bulk statement does not, and it is idempotent.
     */
    @Modifying
    @Query("DELETE FROM TeamTokenEntity t WHERE t.teamId = :teamId")
    fun deleteByTeamId(@Param("teamId") teamId: String)
}