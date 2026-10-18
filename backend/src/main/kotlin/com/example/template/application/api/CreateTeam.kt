package com.example.template.application.api

import com.example.template.domain.AdminUserId
import com.example.template.domain.TournamentId
import com.example.template.domain.Team

/** Inbound port (driving side). Controllers depend on this, never on the implementation. */
interface CreateTeam {
    /**
     * Creates a team with exactly two players in the tournament owned by [owner].
     * The tournament id is the path variable; ownership is enforced against the
     * authenticated admin supplied by the controller, never from the request body.
     */
    fun create(tournamentId: TournamentId, command: TeamWriteRequest, owner: AdminUserId): Team
}
