package tichu.application.api

import tichu.domain.AdminUserId
import tichu.domain.TournamentId
import java.time.Instant

/**
 * Inbound port (driving side). Lists the organizers of a tournament: the lead organizer
 * and the co-organizers. Any organizer may read the list; a stranger gets not-found (#131).
 */
interface ListOrganizers {
    fun list(tournamentId: TournamentId, caller: AdminUserId): Organizers
}

/** The organizers of one tournament: exactly one [lead] plus the [coOrganizers] in the order they were added. */
data class Organizers(
    val lead: Organizer,
    val coOrganizers: List<Organizer>,
)

/**
 * One organizer as the organizer list shows it. [addedAt] is null for the lead organizer,
 * who is fixed for the tournament's lifetime and was never added.
 */
data class Organizer(
    val adminUserId: AdminUserId,
    val email: String,
    val addedAt: Instant?,
)
