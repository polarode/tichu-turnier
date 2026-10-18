package com.example.template.application.api

import com.example.template.domain.Team
/**
 * Inbound port (driving side) for the team-session-scoped read of the team's own
 * data. The principal is the team, so the only thing it may ever read is itself —
 * the use case resolves the principal's own team and nothing else.
 */
interface GetOwnTeam {
    fun get(principal: AuthenticatedTeam): Team
}