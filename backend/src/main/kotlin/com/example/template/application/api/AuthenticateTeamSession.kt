package com.example.template.application.api

import com.example.template.domain.TeamId
import com.example.template.domain.TournamentId

/** Inbound port (driving side). Resolves a raw team session token to its team. */
interface AuthenticateTeamSession {
    /**
     * Throws `UnauthenticatedException` for a missing, expired or unknown team
     * session. Read-only: the session is never rotated on ordinary requests.
     */
    fun authenticate(rawToken: String): AuthenticatedTeam
}

/** The identity established by a valid team session — exactly the team's own scope. */
data class AuthenticatedTeam(
    val teamId: TeamId,
    val tournamentId: TournamentId,
)