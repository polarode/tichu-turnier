package com.example.template.application.api

import com.example.template.domain.TeamId
import com.example.template.domain.TournamentId

/**
 * Inbound port (driving side). Exchanges a raw team access token (from the magic
 * link) for a team session. Unknown, revoked and completed-tournament tokens all
 * fail with the same generic rejection so nothing about a token's existence is
 * disclosed; a link is multi-use — both team members share it.
 */
interface ExchangeTeamAccess {
    fun exchange(rawToken: String): IssuedTeamSession
}

/** The freshly issued team session: the raw token goes only into the httpOnly cookie. */
data class IssuedTeamSession(
    val teamId: TeamId,
    val tournamentId: TournamentId,
    val token: String,
    val expiresAt: java.time.Instant,
)