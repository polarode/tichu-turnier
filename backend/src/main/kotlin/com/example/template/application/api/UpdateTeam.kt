package com.example.template.application.api

import com.example.template.domain.AdminUserId
import com.example.template.domain.Team
import com.example.template.domain.TeamId
import com.example.template.domain.TournamentId

/** Inbound port (driving side). Controllers depend on this, never on the implementation. */
interface UpdateTeam {
    /**
     * Replaces the name and/or the two players of an existing team in the tournament
     * owned by [owner]. The full team validation matrix is re-run against the other
     * teams of the tournament, with this team excluded from the uniqueness checks.
     *
     * Fails as not-found when the tournament or the team does not exist, or when
     * either belongs to another admin. Field rules and uniqueness violations surface
     * as the same typed, field-anchored errors as creation.
     */
    fun update(
        tournamentId: TournamentId,
        teamId: TeamId,
        command: TeamWriteRequest,
        owner: AdminUserId,
    ): Team
}
