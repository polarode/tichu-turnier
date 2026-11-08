package tichu.infra.persistence

import tichu.domain.AdminUserId
import tichu.domain.CoOrganizer
import tichu.domain.CoOrganizerRepository
import tichu.domain.TournamentId

/**
 * Driven adapter implementing [CoOrganizerRepository] on top of Spring Data JPA and
 * PostgreSQL (schema managed by Flyway). A plain class, wired as a bean by
 * [PersistenceConfiguration].
 */
class JpaCoOrganizerRepository(
    private val springData: SpringDataCoOrganizerRepository,
) : CoOrganizerRepository {

    override fun add(coOrganizer: CoOrganizer) {
        springData.save(
            CoOrganizerEntity(
                tournamentId = coOrganizer.tournamentId.value,
                adminUserId = coOrganizer.adminUserId.value,
                addedAt = coOrganizer.addedAt,
            ),
        )
    }

    override fun isCoOrganizer(tournamentId: TournamentId, adminUserId: AdminUserId): Boolean =
        springData.existsByTournamentIdAndAdminUserId(tournamentId.value, adminUserId.value)

    override fun findByTournament(tournamentId: TournamentId): List<CoOrganizer> =
        springData.findAllByTournamentIdOrderByAddedAtAsc(tournamentId.value).map {
            CoOrganizer(
                tournamentId = TournamentId(it.tournamentId),
                adminUserId = AdminUserId(it.adminUserId),
                addedAt = it.addedAt,
            )
        }

    override fun findTournamentIdsByAdmin(adminUserId: AdminUserId): List<TournamentId> =
        springData.findAllByAdminUserId(adminUserId.value).map { TournamentId(it.tournamentId) }
}
