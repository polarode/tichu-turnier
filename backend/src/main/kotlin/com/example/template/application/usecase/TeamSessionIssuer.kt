package com.example.template.application.usecase

import com.example.template.application.api.IssuedTeamSession
import com.example.template.domain.IdGenerator
import com.example.template.domain.SessionId
import com.example.template.domain.SessionTokens
import com.example.template.domain.Team
import com.example.template.domain.TeamSession
import com.example.template.domain.TeamSessionRepository
import java.time.Duration
import java.time.Instant

/**
 * Shared collaborator that turns a known team into a persisted team session and
 * returns the raw token plus its expiry. Mirrors [AdminSessionIssuer] so team
 * sessions are issued through exactly one path; the TTL comes from configuration
 * (12h, shorter than admin sessions because a team session is tied to match day).
 */
class TeamSessionIssuer(
    private val sessions: TeamSessionRepository,
    private val sessionTokens: SessionTokens,
    private val idGenerator: IdGenerator,
    private val sessionTtl: Duration,
) {

    fun issue(team: Team, now: Instant): IssuedTeamSession {
        val rawToken = sessionTokens.generate()
        val session = TeamSession(
            id = SessionId(idGenerator.newId()),
            teamId = team.id,
            tokenHash = sessionTokens.hash(rawToken),
            createdAt = now,
            expiresAt = now.plus(sessionTtl),
        )
        sessions.save(session)
        return IssuedTeamSession(
            teamId = team.id,
            tournamentId = team.tournamentId,
            token = rawToken,
            expiresAt = session.expiresAt,
        )
    }
}