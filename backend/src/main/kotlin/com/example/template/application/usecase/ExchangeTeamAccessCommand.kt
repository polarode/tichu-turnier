package com.example.template.application.usecase

import com.example.template.application.api.ExchangeTeamAccess
import com.example.template.application.api.IssuedTeamSession
import com.example.template.domain.SessionTokens
import com.example.template.domain.TeamRepository
import com.example.template.domain.TeamTokenRepository
import com.example.template.domain.TournamentRepository
import com.example.template.domain.TournamentStatus
import com.example.template.vocabulary.TeamAccessException
import java.time.Clock

/**
 * Exchanges a raw team access token for a team session (Phase 5).
 *
 * The token is validated by digest only; every rejection path — unknown token,
 * revoked token, team gone, tournament completed — throws the same generic
 * [TeamAccessException], so a caller cannot learn whether a guessed token ever
 * existed. There is deliberately no distinct "expired tournament" code: the link
 * is dead the moment the tournament completes and must be indistinguishable from
 * an unknown one. A valid link is multi-use — both team members may exchange it,
 * each receiving their own team session.
 */
class ExchangeTeamAccessCommand(
    private val teamTokens: TeamTokenRepository,
    private val teamRepository: TeamRepository,
    private val tournamentRepository: TournamentRepository,
    private val sessionTokens: SessionTokens,
    private val teamSessionIssuer: TeamSessionIssuer,
    private val clock: Clock,
) : ExchangeTeamAccess {

    override fun exchange(rawToken: String): IssuedTeamSession {
        val token = teamTokens.findByTokenHash(sessionTokens.hash(rawToken))
            ?: throw rejected()
        if (token.isRevoked()) throw rejected()

        // The secret has been validated, so the unscoped team lookup is safe here:
        // it resolves the token's own team only.
        val team = teamRepository.findById(token.teamId) ?: throw rejected()
        val tournament = tournamentRepository.findById(team.tournamentId) ?: throw rejected()
        if (tournament.status == TournamentStatus.COMPLETED) throw rejected()

        return teamSessionIssuer.issue(team, clock.instant())
    }

    private fun rejected() = TeamAccessException("This access link is not valid")
}