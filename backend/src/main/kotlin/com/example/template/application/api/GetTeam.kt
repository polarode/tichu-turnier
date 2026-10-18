package com.example.template.application.api

import com.example.template.domain.AdminUserId
import com.example.template.domain.Team
import com.example.template.domain.TeamId
import com.example.template.domain.TournamentId

interface GetTeam {
    /**
     * Fails as not-found when the tournament or the team does not exist, or when
     * either belongs to another admin.
     */
    fun get(tournamentId: TournamentId, teamId: TeamId, owner: AdminUserId): Team
}
