package tichu.application.api

import tichu.domain.AdminUserId
import tichu.domain.TournamentId

/**
 * Inbound port (driving side). Lead-only: grants an active admin, named by email, access to
 * the tournament as co-organizer. Returns the updated organizer list (#131).
 */
interface AddCoOrganizer {
    /**
     * Fails as not-found for a stranger, forbidden for a co-organizer, and with a field-anchored
     * error for an email that is not an active admin, the lead organizer's own, or an admin who
     * already co-organizes the tournament.
     */
    fun add(tournamentId: TournamentId, email: String, caller: AdminUserId): Organizers
}
