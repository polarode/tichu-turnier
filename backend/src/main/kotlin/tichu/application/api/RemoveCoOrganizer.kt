package tichu.application.api

import tichu.domain.AdminUserId
import tichu.domain.TournamentId

/**
 * Inbound port (driving side). Ends an admin's co-organizer access to a tournament (#132).
 * Covers two cases: the lead organizer removes a co-organizer, and a co-organizer leaves by
 * targeting their own id.
 */
interface RemoveCoOrganizer {
    /**
     * Removes [adminUserId] as co-organizer of the tournament. Fails as not-found for a
     * stranger, and as forbidden for a co-organizer who targets anyone other than themself.
     * The lead organizer targeting their own id fails as `LEAD_ORGANIZER_CANNOT_LEAVE`.
     * Removing an admin who is not a co-organizer succeeds without effect.
     */
    fun remove(tournamentId: TournamentId, adminUserId: AdminUserId, caller: AdminUserId)
}
