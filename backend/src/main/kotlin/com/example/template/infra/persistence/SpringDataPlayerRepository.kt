package com.example.template.infra.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional

/**
 * Spring Data repository for [PlayerEntity]. It is an implementation detail of the
 * JPA adapter — never exposed outside `infra/persistence`. Players are always loaded
 * as part of their [TeamEntity] aggregate via [JpaTeamRepository].
 */
interface SpringDataPlayerRepository : JpaRepository<PlayerEntity, String> {
    fun findAllByTournamentId(tournamentId: String): List<PlayerEntity>
    fun findAllByTeamId(teamId: String): List<PlayerEntity>
    fun findAllByTeamIdIn(teamIds: List<String>): List<PlayerEntity>

    /**
     * Replace-on-update: removes the players of one team before new rows are written.
     * A bulk delete (with the persistence context cleared afterwards) is used so a
     * re-inserted player id cannot collide with a still-tracked removed entity.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("delete from PlayerEntity p where p.teamId = :teamId")
    fun deleteAllByTeamId(teamId: String)
}
