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
    val lead: LeadOrganizer,
    val coOrganizers: List<CoOrganizerEntry>,
)

/** The lead organizer as the organizer list shows it. Fixed for the tournament's lifetime, never added. */
data class LeadOrganizer(
    val adminUserId: AdminUserId,
    val email: String,
)

/** A co-organizer as the organizer list shows it, with the moment the access was granted. */
data class CoOrganizerEntry(
    val adminUserId: AdminUserId,
    val email: String,
    val addedAt: Instant,
)
