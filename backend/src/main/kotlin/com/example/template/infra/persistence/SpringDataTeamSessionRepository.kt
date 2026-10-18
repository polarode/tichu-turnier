package com.example.template.infra.persistence

import org.springframework.data.jpa.repository.JpaRepository

/** Spring Data repository for [TeamSessionEntity]. Implementation detail of the JPA adapter. */
interface SpringDataTeamSessionRepository : JpaRepository<TeamSessionEntity, String> {
    fun findByTokenHash(tokenHash: String): TeamSessionEntity?
}