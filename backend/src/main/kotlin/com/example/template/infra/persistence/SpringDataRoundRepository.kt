package com.example.template.infra.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.transaction.annotation.Transactional

/**
 * Spring Data repositories for [RoundEntity] / [RoundTableEntity]. They are
 * implementation details of the JPA adapter — never exposed outside
 * `infra/persistence`. The application-facing port is
 * [com.example.template.domain.RoundRepository], implemented by [JpaRoundRepository].
 */
interface SpringDataRoundRepository : JpaRepository<RoundEntity, String> {
    fun findByTournamentIdAndRoundNumber(tournamentId: String, roundNumber: Int): RoundEntity?
    fun findByTournamentIdOrderByRoundNumberAsc(tournamentId: String): List<RoundEntity>
}

interface SpringDataRoundTableRepository : JpaRepository<RoundTableEntity, Long> {
    fun findAllByRoundIdIn(roundIds: Collection<String>): List<RoundTableEntity>

    /** Derived deletes run outside `SimpleJpaRepository`, so they carry their own transaction. */
    @Transactional
    fun deleteAllByRoundId(roundId: String)
}
