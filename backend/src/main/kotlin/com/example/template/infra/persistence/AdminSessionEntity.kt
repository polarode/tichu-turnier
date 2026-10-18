package com.example.template.infra.persistence

import com.example.template.domain.AdminSession
import com.example.template.domain.AdminUserId
import com.example.template.domain.SessionId
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * Persistence model for [AdminSession]. Only the token digest is stored; the raw
 * token exists solely in the client's cookie.
 */
@Entity
@Table(name = "admin_sessions")
class AdminSessionEntity(
    @Id
    val id: String,
    val adminUserId: String,
    val tokenHash: String,
    val createdAt: Instant,
    val expiresAt: Instant,
) {
    fun toDomain(): AdminSession = AdminSession(
        id = SessionId(id),
        adminUserId = AdminUserId(adminUserId),
        tokenHash = tokenHash,
        createdAt = createdAt,
        expiresAt = expiresAt,
    )

    companion object {
        fun from(session: AdminSession): AdminSessionEntity = AdminSessionEntity(
            id = session.id.value,
            adminUserId = session.adminUserId.value,
            tokenHash = session.tokenHash,
            createdAt = session.createdAt,
            expiresAt = session.expiresAt,
        )
    }
}
