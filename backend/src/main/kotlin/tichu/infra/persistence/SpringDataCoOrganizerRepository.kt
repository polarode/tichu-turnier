package tichu.infra.persistence

import org.springframework.data.jpa.repository.JpaRepository

/**
 * Spring Data repository for [CoOrganizerEntity]; an implementation detail of
 * [JpaCoOrganizerRepository], never exposed outside `infra/persistence`.
 */
interface SpringDataCoOrganizerRepository : JpaRepository<CoOrganizerEntity, CoOrganizerKey> {
    fun existsByTournamentIdAndAdminUserId(tournamentId: String, adminUserId: String): Boolean

    fun findAllByTournamentIdOrderByAddedAtAsc(tournamentId: String): List<CoOrganizerEntity>

    fun findAllByAdminUserId(adminUserId: String): List<CoOrganizerEntity>
}
