package tichu.infra.persistence

import org.springframework.data.jpa.repository.JpaRepository
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
    fun findByIdAndTournamentId(id: String, tournamentId: String): TeamEntity?

    /** Scoped delete so a team can only be removed from the tournament it belongs to. Derived deletes run outside `SimpleJpaRepository`, so they carry their own transaction. */
    @Transactional
    fun deleteByIdAndTournamentId(id: String, tournamentId: String)
}
