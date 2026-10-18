package com.example.template.infra.persistence

import com.example.template.domain.Team
import com.example.template.domain.TeamId
import com.example.template.domain.TournamentId
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * Persistence model for [Team]. Lives in the infra layer — the domain entity stays
 * framework-free. Teams are never updated after creation, so fields are immutable
 * from the adapter's point of view.
 *
 * The players are a separate [PlayerEntity] table referencing this one with
 * ON DELETE CASCADE (see the V5 migration); the JPA adapter loads them per team
 * and hands them to the domain. Uniqueness of `name` is per tournament and backed
 * by a database constraint, so the application-level check cannot be raced around.
 */
@Entity
@Table(name = "teams")
class TeamEntity(
    @Id
    val id: String,
    @Column(name = "tournament_id", nullable = false)
    val tournamentId: String,
    @Column(name = "name", nullable = false)
    val name: String,
) {
    fun toDomain(players: List<PlayerEntity>): Team = Team(
        id = TeamId(id),
        tournamentId = TournamentId(tournamentId),
        name = name,
        players = players.map { it.toDomain() },
    )

    companion object {
        fun from(team: Team): TeamEntity = TeamEntity(
            id = team.id.value,
            tournamentId = team.tournamentId.value,
            name = team.name,
        )
    }
}
