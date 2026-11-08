package tichu.testsupport

import tichu.domain.AdminUserId
import tichu.domain.CoOrganizer
import tichu.domain.CoOrganizerRepository
import tichu.domain.TournamentId

/** In-memory double for [CoOrganizerRepository], used by pure use-case tests. */
class InMemoryCoOrganizerRepository : CoOrganizerRepository {

    private val rows = LinkedHashMap<Pair<TournamentId, AdminUserId>, CoOrganizer>()

    override fun add(coOrganizer: CoOrganizer) {
        rows[coOrganizer.tournamentId to coOrganizer.adminUserId] = coOrganizer
    }

    override fun isCoOrganizer(tournamentId: TournamentId, adminUserId: AdminUserId): Boolean =
        rows.containsKey(tournamentId to adminUserId)

    override fun findByTournament(tournamentId: TournamentId): List<CoOrganizer> =
        rows.values.filter { it.tournamentId == tournamentId }.sortedBy { it.addedAt }

    override fun findTournamentIdsByAdmin(adminUserId: AdminUserId): List<TournamentId> =
        rows.values.filter { it.adminUserId == adminUserId }.map { it.tournamentId }
}
