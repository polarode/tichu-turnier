package tichu.application.usecase

import tichu.application.api.AuthenticateTeamSession
import tichu.application.api.AuthenticatedTeam
import tichu.domain.SessionTokens
import tichu.domain.TeamRepository
import tichu.domain.TeamSessionRepository
import tichu.vocabulary.UnauthenticatedException
import java.time.Clock

/**
 * Resolves a raw team session token to the team it belongs to, for the API-wide
 * team guard. Read-only by design; the team row is re-read on every request so a
 * deleted team loses its session immediately rather than at expiry.
 */
class AuthenticateTeamSessionQuery(
    private val teamSessions: TeamSessionRepository,
    private val teamRepository: TeamRepository,
    private val sessionTokens: SessionTokens,
    private val clock: Clock,
) : AuthenticateTeamSession {

    override fun authenticate(rawToken: String): AuthenticatedTeam {
        val session = teamSessions.findByTokenHash(sessionTokens.hash(rawToken))
            ?: throw unauthenticated()
        if (session.isExpired(clock.instant())) throw unauthenticated()
        val team = teamRepository.findById(session.teamId) ?: throw unauthenticated()
        return AuthenticatedTeam(teamId = team.id, tournamentId = team.tournamentId)
    }

    private fun unauthenticated() = UnauthenticatedException("Session is invalid or has expired")
}