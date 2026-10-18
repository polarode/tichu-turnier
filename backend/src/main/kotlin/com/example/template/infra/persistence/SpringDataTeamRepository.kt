package com.example.template.infra.persistence

import org.springframework.data.jpa.repository.JpaRepository

/**
 * Spring Data repository for [TeamEntity]. It is an implementation detail of
 * the JPA adapter — never exposed outside `infra/persistence`. The application-facing
 * port is [com.example.template.domain.TeamRepository], implemented by
 * [JpaTeamRepository].
 */
interface SpringDataTeamRepository : JpaRepository<TeamEntity, String> {
    fun findAllByTournamentId(tournamentId: String): List<TeamEntity>
    fun findByIdAndTournamentId(id: String, tournamentId: String): TeamEntity?
}
