package tichu.infra.persistence

import tichu.domain.Pairing
import tichu.domain.Round
import tichu.domain.RoundId
import tichu.domain.Table as PairingTable
import tichu.domain.TableId
import tichu.domain.TeamId
import tichu.domain.TournamentId
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * Persistence model for [Round]. Lives in the infra layer — the domain entity stays
 * framework-free. The pairing's tables are a separate [RoundTableEntity] table
 * referencing this one with ON DELETE CASCADE (see the V12 migration); the adapter
 * loads them per round and reassembles the domain aggregate.
 */
@Entity
@Table(name = "rounds")
class RoundEntity(
    @Id
    val id: String,
    @Column(name = "tournament_id", nullable = false)
    val tournamentId: String,
    @Column(name = "round_number", nullable = false)
    val roundNumber: Int,
) {
    fun toDomain(tables: List<RoundTableEntity>): Round = Round(
        id = RoundId(id),
        tournamentId = TournamentId(tournamentId),
        number = roundNumber,
        pairing = Pairing(
            tables = tables
                .sortedBy { it.tableNumber }
                .map { it.toDomain() },
        ),
    )

    companion object {
        fun from(round: Round): RoundEntity = RoundEntity(
            id = round.id.value,
            tournamentId = round.tournamentId.value,
            roundNumber = round.number,
        )
    }
}

@Entity
@Table(name = "round_tables")
class RoundTableEntity(
    /** Surrogate database id; assigned by the sequence, never by application code. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,
    @Column(name = "round_id", nullable = false)
    val roundId: String,
    @Column(name = "tournament_id", nullable = false)
    val tournamentId: String,
    @Column(name = "table_number", nullable = false)
    val tableNumber: Int,
    @Column(name = "seat1_team_id", nullable = false)
    val seat1TeamId: String,
    @Column(name = "seat2_team_id", nullable = false)
    val seat2TeamId: String,
    /** The third seat, only at the trio table; null at a normal table. */
    @Column(name = "seat3_team_id")
    val seat3TeamId: String?,
) {
    fun toDomain(): PairingTable = PairingTable(
        id = TableId(tableNumber),
        seatedTeams = listOfNotNull(seat1TeamId, seat2TeamId, seat3TeamId).map(::TeamId),
    )

    companion object {
        fun from(roundId: String, tournamentId: String, table: PairingTable): RoundTableEntity =
            RoundTableEntity(
                roundId = roundId,
                tournamentId = tournamentId,
                tableNumber = table.id.value,
                seat1TeamId = table.seatedTeams[0].value,
                seat2TeamId = table.seatedTeams[1].value,
                seat3TeamId = table.seatedTeams.getOrNull(2)?.value,
            )
    }
}
