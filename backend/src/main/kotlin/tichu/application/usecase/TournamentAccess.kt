package tichu.application.usecase

import tichu.domain.AdminUserId
import tichu.domain.Tournament
import tichu.domain.TournamentId
import tichu.domain.TournamentRepository
import tichu.vocabulary.ForbiddenException
import tichu.vocabulary.NotFoundException

/**
 * The single place the organizer rule is applied on a single tournament. A caller who
 * does not organize the tournament fails exactly as if it did not exist, so a stranger
 * cannot even learn that the id exists. Returning not-found (rather than forbidden) is
 * the rule; [ledOrThrow] keeps it for strangers and adds the lead check on top.
 *
 * Verbs that any organizer may run use [organizedOrThrow]. Lead-only verbs (deleting
 * the tournament, managing its co-organizers) use [ledOrThrow]. Shared so every verb
 * (edit, delete, start, …) cannot drift apart.
 */
class TournamentAccess(
    private val tournamentRepository: TournamentRepository,
) {

    /** The tournament, if [admin] organizes it. Otherwise not-found, as for a missing id. */
    fun organizedOrThrow(id: TournamentId, admin: AdminUserId): Tournament {
        val tournament = tournamentRepository.findById(id)
        if (tournament == null || !tournament.isOwnedBy(admin)) {
            throw NotFoundException("Tournament ${id.value} not found")
        }
        return tournament
    }

    /**
     * The tournament, if [admin] is its lead organizer. Strangers get not-found; a
     * co-organizer gets forbidden. The lead organizer is the tournament's `ownerId`.
     */
    fun ledOrThrow(id: TournamentId, admin: AdminUserId): Tournament {
        val tournament = organizedOrThrow(id, admin)
        if (tournament.ownerId != admin) {
            throw ForbiddenException("Only the lead organizer may do this on tournament ${id.value}")
        }
        return tournament
    }
}
