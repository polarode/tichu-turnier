package tichu.infra.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * Persistence model for one team's confirmation of a round table (V16). Keyed by
 * the table's natural key plus the team; the surrogate id is internal only.
 */
@Entity
@Table(name = "table_confirmations")
class TableConfirmationEntity(
    /** Surrogate database id; assigned by the sequence, never by application code. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "tournament_id", nullable = false)
    val tournamentId: String,
    @Column(name = "round_number", nullable = false)
    val roundNumber: Int,
    @Column(name = "table_number", nullable = false)
    val tableNumber: Int,
    @Column(name = "team_id", nullable = false)
    val teamId: String,
    @Column(name = "confirmed_at", nullable = false)
    val confirmedAt: Instant,
)
