package com.example.template.application.api

import com.example.template.domain.AdminUserId
import com.example.template.domain.TeamId
import com.example.template.domain.TournamentId

/**
 * Inbound port (driving side). Owner-only: disables the team's access link —
 * further exchanges fail with the generic rejection, and the team's live sessions
 * end on their next request. Idempotent; only rotation revives the link.
 */
interface RevokeTeamAccessLink {
    fun revoke(tournamentId: TournamentId, teamId: TeamId, owner: AdminUserId)
}