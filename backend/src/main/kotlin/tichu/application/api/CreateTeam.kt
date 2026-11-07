package tichu.application.api

import tichu.domain.AdminUserId
import tichu.domain.TournamentId
import tichu.domain.Team

/** Inbound port (driving side). Controllers depend on this, never on the implementation. */
interface CreateTeam {
    /**
     * Creates a team with exactly two players in the tournament owned by [owner].
     * The tournament id is the path variable; ownership is enforced against the
     * authenticated admin supplied by the controller, never from the request body.
     *
     * Answers as [tichu.vocabulary.TournamentNotEditableException] (`409 TOURNAMENT_NOT_EDITABLE`)
     * once the tournament has left the setup state.
     */
    fun create(tournamentId: TournamentId, command: TeamWriteRequest, owner: AdminUserId): Team
}
