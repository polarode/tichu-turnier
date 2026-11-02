package tichu.infra.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional

/**
 * Spring Data repositories for [RoundEntity] / [RoundTableEntity]. They are
 * implementation details of the JPA adapter — never exposed outside
 * `infra/persistence`. The application-facing port is
 * [tichu.domain.RoundRepository], implemented by [JpaRoundRepository].
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

    /** `SELECT … FOR UPDATE` on one round table row; blocks until a concurrent holder commits. */
    @Query(
        value = """
            SELECT rt.id FROM round_tables rt
            JOIN rounds r ON r.id = rt.round_id
            WHERE r.tournament_id = :tournamentId
              AND r.round_number = :roundNumber
              AND rt.table_number = :tableNumber
            FOR UPDATE OF rt
        """,
        nativeQuery = true,
    )
    fun lockTable(
        @Param("tournamentId") tournamentId: String,
        @Param("roundNumber") roundNumber: Int,
        @Param("tableNumber") tableNumber: Int,
    ): List<Long>
}
