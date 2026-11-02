package tichu.infra.persistence

import tichu.domain.TeamId
import tichu.domain.TeamToken
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * Persistence model for [TeamToken]. Exactly one row per team (`team_id` is the
 * primary key and cascades on team deletion); only the token digest is stored.
 */
@Entity
@Table(name = "team_tokens")
class TeamTokenEntity(
    @Id
    val teamId: String,
    val tokenHash: String,
    val revealed: Boolean,
    val revokedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    fun toDomain(): TeamToken = TeamToken(
        teamId = TeamId(teamId),
        tokenHash = tokenHash,
        revealed = revealed,
        revokedAt = revokedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    companion object {
        fun from(token: TeamToken): TeamTokenEntity = TeamTokenEntity(
            teamId = token.teamId.value,
            tokenHash = token.tokenHash,
            revealed = token.revealed,
            revokedAt = token.revokedAt,
            createdAt = token.createdAt,
            updatedAt = token.updatedAt,
        )
    }
}