package com.example.template.application.api

import com.example.template.domain.Team

/**
 * The team's identity view as served by `GET /api/team/me`: the team itself plus
 * the display name of the tournament it plays in. The name is resolved from the
 * principal's own tournament — the session, not the request, scopes every field.
 */
data class TeamOverview(
    val team: Team,
    val tournamentName: String,
)

/**
 * Inbound port (driving side) for the team-session-scoped read of the team's own
 * data. The principal is the team, so the only thing it may ever read is itself —
 * the use case resolves the principal's own team and nothing else.
 */
interface GetOwnTeam {
    fun get(principal: AuthenticatedTeam): TeamOverview
}
