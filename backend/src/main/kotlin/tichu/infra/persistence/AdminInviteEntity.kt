package tichu.infra.persistence

import tichu.domain.AdminInvite
import tichu.domain.AdminUserId
import tichu.domain.InviteId
import tichu.domain.InviteStatus
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * Persistence model for [AdminInvite]. Lives in the infra layer — the domain entity
 * stays framework-free. The secret is stored only as `tokenHash`; there is deliberately
 * no raw-token column, so the database can never return one.
 */
@Entity
@Table(name = "admin_invites")
class AdminInviteEntity(
    @Id
    val id: String,
    val label: String,
    val tokenHash: String,
    val status: String,
    val createdBy: String,
    val createdAt: Instant,
    val expiresAt: Instant,
    /** Null until the invite is accepted. */
    val spentAt: Instant?,
) {
    fun toDomain(): AdminInvite = AdminInvite(
        id = InviteId(id),
        label = label,
        tokenHash = tokenHash,
        status = InviteStatus.valueOf(status),
        createdBy = AdminUserId(createdBy),
        createdAt = createdAt,
        expiresAt = expiresAt,
        spentAt = spentAt,
    )

    companion object {
        fun from(invite: AdminInvite): AdminInviteEntity = AdminInviteEntity(
            id = invite.id.value,
            label = invite.label,
            tokenHash = invite.tokenHash,
            status = invite.status.name,
            createdBy = invite.createdBy.value,
            createdAt = invite.createdAt,
            expiresAt = invite.expiresAt,
            spentAt = invite.spentAt,
        )
    }
}
