package tichu.infra.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/**
 * Spring Data repository for [TournamentEntity]. It is an implementation detail of
 * the JPA adapter — never exposed outside `infra/persistence`. The application-facing
 * port is [tichu.domain.TournamentRepository], implemented by
 * [JpaTournamentRepository].
 */
interface SpringDataTournamentRepository : JpaRepository<TournamentEntity, String> {
    fun findAllByOwnerId(ownerId: String): List<TournamentEntity>

    /** `SELECT … FOR UPDATE` on the tournament row; blocks until a concurrent holder commits. */
    @Query(value = "SELECT id FROM tournaments WHERE id = :id FOR UPDATE", nativeQuery = true)
    fun lock(@Param("id") id: String): List<String>
}
