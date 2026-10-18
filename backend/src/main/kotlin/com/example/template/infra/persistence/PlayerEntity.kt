package com.example.template.infra.persistence

import com.example.template.domain.Player
import com.example.template.domain.PlayerId
import com.example.template.domain.TeamId
import com.example.template.domain.TournamentId
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * Persistence model for [Player]. Lives in the infra layer — the domain entity
 * stays framework-free. Players are tournament-scoped (no global player table):
 * the same name may recur in another tournament, but within one tournament each
 * name exists exactly once (see the V5 migration's unique constraint), which is
 * what keeps a player off two teams. A team update replaces its two player rows,
 * preserving the player ids and changing the names.
 */
@Entity
@Table(name = "players")
class PlayerEntity(
    @Id
    val id: String,
    @Column(name = "tournament_id", nullable = false)
    val tournamentId: String,
    @Column(name = "team_id", nullable = false)
    val teamId: String,
    @Column(name = "name", nullable = false)
    val name: String,
) {
    fun toDomain(): Player = Player(
        id = PlayerId(id),
        tournamentId = TournamentId(tournamentId),
        teamId = TeamId(teamId),
        name = name,
    )

    companion object {
        fun from(player: Player): PlayerEntity = PlayerEntity(
            id = player.id.value,
            tournamentId = player.tournamentId.value,
            teamId = player.teamId.value,
            name = player.name,
        )
    }
}
