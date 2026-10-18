package com.example.template.infra.persistence

import com.example.template.domain.TeamId
import com.example.template.domain.TeamToken
import com.example.template.domain.TeamTokenRepository

/**
 * Driven adapter implementing [TeamTokenRepository] on top of Spring Data JPA and
 * PostgreSQL. The token row is upserted by its team id (the primary key), so
 * provisioning is idempotent per team.
 */
class JpaTeamTokenRepository(
    private val springData: SpringDataTeamTokenRepository,
) : TeamTokenRepository {

    override fun save(token: TeamToken): TeamToken =
        springData.save(TeamTokenEntity.from(token)).toDomain()

    override fun findByTeamId(teamId: TeamId): TeamToken? =
        springData.findByTeamId(teamId.value)?.toDomain()

    override fun findByTokenHash(tokenHash: String): TeamToken? =
        springData.findByTokenHash(tokenHash)?.toDomain()

    override fun deleteByTeamId(teamId: TeamId) {
        springData.deleteByTeamId(teamId.value)
    }
}