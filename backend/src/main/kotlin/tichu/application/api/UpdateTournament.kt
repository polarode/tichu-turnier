package tichu.application.api

import tichu.domain.AdminUserId
import tichu.domain.Tournament
import tichu.domain.TournamentId

/** Inbound port (driving side). Controllers depend on this, never on the implementation. */
interface UpdateTournament {
    /**
     * Replaces the editable configuration of the tournament owned by [owner] — the
     * authenticated admin supplied by the controller, never read from the body.
     *
     * Fails as not-found when [id] does not exist or is owned by another admin, and as
     * [tichu.vocabulary.TournamentNotEditableException] when the tournament
     * has left the setup state.
     */
    fun update(id: TournamentId, command: TournamentWriteRequest, owner: AdminUserId): Tournament
}
