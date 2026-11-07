package tichu.application.api

import tichu.domain.AdminUserId
import tichu.domain.Team
import tichu.domain.TeamId
import tichu.domain.TournamentId

/** Inbound port (driving side). Controllers depend on this, never on the implementation. */
interface UpdateTeam {
    /**
     * Replaces the name and/or the two players of an existing team in the tournament
     * owned by [owner]. The full team validation matrix is re-run against the other
     * teams of the tournament, with this team excluded from the uniqueness checks.
     *
     * Fails as not-found when the tournament or the team does not exist, or when
     * either belongs to another admin. Fails as [tichu.vocabulary.TournamentNotEditableException]
     * (`409 TOURNAMENT_NOT_EDITABLE`) once the tournament has left the setup state, before
     * any field validation. Field rules and uniqueness violations surface as the same
     * typed, field-anchored errors as creation.
     */
    fun update(
        tournamentId: TournamentId,
        teamId: TeamId,
        command: TeamWriteRequest,
        owner: AdminUserId,
    ): Team
}
