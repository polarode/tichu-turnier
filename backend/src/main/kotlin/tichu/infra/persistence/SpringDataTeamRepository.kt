package tichu.infra.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional

/**
 * Spring Data repository for [TeamEntity]. It is an implementation detail of
 * the JPA adapter — never exposed outside `infra/persistence`. The application-facing
 * port is [tichu.domain.TeamRepository], implemented by
 * [JpaTeamRepository].
 */
interface SpringDataTeamRepository : JpaRepository<TeamEntity, String> {
    fun findAllByTournamentId(tournamentId: String): List<TeamEntity>
    fun countByTournamentId(tournamentId: String): Int

    /** One row per tournament that has teams: a single `GROUP BY` for the whole id list. */
    @Query("SELECT t.tournamentId AS tournamentId, COUNT(t) AS teamCount FROM TeamEntity t WHERE t.tournamentId IN :tournamentIds GROUP BY t.tournamentId")
    fun countTeamsByTournamentIds(@Param("tournamentIds") tournamentIds: Collection<String>): List<TeamCountRow>

    /** Projection of [countTeamsByTournamentIds]. */
    interface TeamCountRow {
        val tournamentId: String
        val teamCount: Long
    }
    fun findByIdAndTournamentId(id: String, tournamentId: String): TeamEntity?

    /** Scoped delete so a team can only be removed from the tournament it belongs to. Derived deletes run outside `SimpleJpaRepository`, so they carry their own transaction. */
    @Transactional
    fun deleteByIdAndTournamentId(id: String, tournamentId: String)
}
