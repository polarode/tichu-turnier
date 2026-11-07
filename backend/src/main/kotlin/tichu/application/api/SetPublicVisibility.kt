package tichu.application.api

import tichu.domain.AdminUserId
import tichu.domain.Tournament
import tichu.domain.TournamentId

/** Inbound port (driving side). Controllers depend on this, never on the implementation. */
interface SetPublicVisibility {
    /**
     * Switches the public visibility of the tournament owned by [owner] on or off.
     * Allowed in every Tournament Status: there is no status gate.
     *
     * Fails as not-found when [id] does not exist or is owned by another admin.
     */
    fun set(id: TournamentId, publicVisibility: Boolean, owner: AdminUserId): Tournament
}
