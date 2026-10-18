package com.example.template.application.api

import com.example.template.domain.AdminUserId
import com.example.template.domain.TeamId
import com.example.template.domain.TournamentId

/** Inbound port (driving side). Controllers depend on this, never on the implementation. */
interface DeleteTeam {
    /**
     * Removes the team and its players from the tournament owned by [owner]. Fails as
     * not-found when the tournament or the team does not exist, or when either belongs
     * to another admin.
     */
    fun delete(tournamentId: TournamentId, teamId: TeamId, owner: AdminUserId)
}
