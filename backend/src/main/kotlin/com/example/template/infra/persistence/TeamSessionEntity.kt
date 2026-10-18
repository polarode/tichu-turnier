package com.example.template.infra.persistence

import com.example.template.domain.SessionId
import com.example.template.domain.TeamId
import com.example.template.domain.TeamSession
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * Persistence model for [TeamSession]. Mirrors [AdminSessionEntity]: only the
 * token digest is stored; the raw token lives in the team's httpOnly cookie.
 */
@Entity
@Table(name = "team_sessions")
class TeamSessionEntity(
    @Id
    val id: String,
    val teamId: String,
    val tokenHash: String,
    val createdAt: Instant,
    val expiresAt: Instant,
) {
    fun toDomain(): TeamSession = TeamSession(
        id = SessionId(id),
        teamId = TeamId(teamId),
        tokenHash = tokenHash,
        createdAt = createdAt,
        expiresAt = expiresAt,
    )

    companion object {
        fun from(session: TeamSession): TeamSessionEntity = TeamSessionEntity(
            id = session.id.value,
            teamId = session.teamId.value,
            tokenHash = session.tokenHash,
            createdAt = session.createdAt,
            expiresAt = session.expiresAt,
        )
    }
}