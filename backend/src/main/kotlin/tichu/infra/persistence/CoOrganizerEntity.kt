package tichu.infra.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant

/**
 * Persistence model for [tichu.domain.CoOrganizer] (V22). Keyed by the pair of tournament
 * and admin, which is how the domain identifies a co-organizer as well.
 */
@Entity
@Table(name = "tournament_co_organizers")
@IdClass(CoOrganizerKey::class)
class CoOrganizerEntity(
    @Id
    @Column(name = "tournament_id", nullable = false)
    val tournamentId: String,
    @Id
    @Column(name = "admin_user_id", nullable = false)
    val adminUserId: String,
    @Column(name = "added_at", nullable = false)
    val addedAt: Instant,
)

/** Composite key of [CoOrganizerEntity]. Defaults give Hibernate its no-arg constructor. */
data class CoOrganizerKey(
    val tournamentId: String = "",
    val adminUserId: String = "",
) : Serializable
