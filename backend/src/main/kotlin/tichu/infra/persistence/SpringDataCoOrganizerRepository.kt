package tichu.infra.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.transaction.annotation.Transactional

/**
 * Spring Data repository for [CoOrganizerEntity]; an implementation detail of
 * [JpaCoOrganizerRepository], never exposed outside `infra/persistence`.
 */
interface SpringDataCoOrganizerRepository : JpaRepository<CoOrganizerEntity, CoOrganizerKey> {
    fun existsByTournamentIdAndAdminUserId(tournamentId: String, adminUserId: String): Boolean

    fun findAllByTournamentIdOrderByAddedAtAsc(tournamentId: String): List<CoOrganizerEntity>

    fun findAllByAdminUserId(adminUserId: String): List<CoOrganizerEntity>

    /** Derived delete runs outside `SimpleJpaRepository`, so it carries its own transaction. Deleting nothing is fine. */
    @Transactional
    fun deleteByTournamentIdAndAdminUserId(tournamentId: String, adminUserId: String)
}
