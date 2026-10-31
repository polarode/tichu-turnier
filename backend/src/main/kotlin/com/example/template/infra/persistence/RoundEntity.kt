package com.example.template.infra.persistence

import com.example.template.domain.Pairing
import com.example.template.domain.Round
import com.example.template.domain.RoundId
import com.example.template.domain.Table as PairingTable
import com.example.template.domain.TableId
import com.example.template.domain.TeamId
import com.example.template.domain.TournamentId
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
    @Column(name = "locked", nullable = false)
    val locked: Boolean = false,
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
        locked = locked,
    )

    companion object {
        fun from(round: Round): RoundEntity = RoundEntity(
            id = round.id.value,
            tournamentId = round.tournamentId.value,
            roundNumber = round.number,
            locked = round.locked,
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
    @Column(name = "home_team_id", nullable = false)
    val homeTeamId: String,
    @Column(name = "away_team_id", nullable = false)
    val awayTeamId: String,
) {
    fun toDomain(): PairingTable = PairingTable(
        id = TableId(tableNumber),
        home = TeamId(homeTeamId),
        away = TeamId(awayTeamId),
    )

    companion object {
        fun from(roundId: String, tournamentId: String, table: PairingTable): RoundTableEntity =
            RoundTableEntity(
                roundId = roundId,
                tournamentId = tournamentId,
                tableNumber = table.id.value,
                homeTeamId = table.home.value,
                awayTeamId = table.away.value,
            )
    }
}
