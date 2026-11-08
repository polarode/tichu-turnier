package tichu.application.usecase

import tichu.application.api.DeleteTournament
import tichu.domain.AdminUserId
import tichu.domain.TournamentId
import tichu.domain.TournamentRepository
import org.springframework.transaction.annotation.Transactional

/**
 * Deletes a tournament together with everything scoped to it. Ownership is checked by
 * loading the tournament first, so a foreign id is a 404 and the row is never touched.
 * The read and the delete share one transaction, so a failure cannot leave a half-done
 * cascade. A plain class wired as a bean in [UseCaseConfiguration].
 */
@Transactional
class DeleteTournamentCommand(
    private val repository: TournamentRepository,
    private val tournamentAccess: TournamentAccess,
) : DeleteTournament {

    override fun delete(id: TournamentId, owner: AdminUserId) {
        tournamentAccess.ledOrThrow(id, owner)
        repository.delete(id)
    }
}
