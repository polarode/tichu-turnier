package com.example.template.infra.persistence

import org.springframework.data.jpa.repository.JpaRepository

/**
 * Spring Data repository for [PlayerEntity]. It is an implementation detail of the
 * JPA adapter — never exposed outside `infra/persistence`. Players are always loaded
 * as part of their [TeamEntity] aggregate via [JpaTeamRepository].
 */
interface SpringDataPlayerRepository : JpaRepository<PlayerEntity, String> {
    fun findAllByTournamentId(tournamentId: String): List<PlayerEntity>
    fun findAllByTeamId(teamId: String): List<PlayerEntity>
    fun findAllByTeamIdIn(teamIds: List<String>): List<PlayerEntity>
}
