package tichu.application.usecase

import tichu.application.api.RevealedTeamAccessLink
import tichu.application.api.RevealTeamAccessLink
import tichu.application.api.RotateTeamAccessLink
import tichu.application.api.RevokeTeamAccessLink
import tichu.domain.AdminUserId
import tichu.domain.Team
import tichu.domain.TeamId
import tichu.domain.TeamRepository
import tichu.domain.TeamSessionRepository
import tichu.domain.TeamToken
import tichu.domain.TeamTokenRepository
import tichu.domain.TournamentId
import tichu.domain.TournamentRepository
import tichu.domain.SessionTokens
import tichu.vocabulary.NotFoundException
import tichu.vocabulary.TeamAccessLinkConflictException
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * The organizer-side lifecycle of one team's access link (#25): reveal (once),
 * rotate (recovery) and revoke. All three verbs share one ownership-and-existence
 * lookup — a foreign or missing tournament/team fails as not-found, exactly like
 * every other team verb — so they cannot drift apart.
 *
 * The raw token is generated fresh at reveal time through the [SessionTokens] port
 * and only ever persisted hashed; rotate mints new material without returning it
 * (the one-time view belongs to reveal alone). Rotation and revocation end the
 * team's live sessions immediately, satisfying "on their next request" without
 * waiting for expiry or the sweep.
 *
 * Each verb touches the token and possibly the session table, so the command is
 * transactional.
 */
@Transactional
class ManageTeamAccessLinkCommand(
    private val tournamentAccess: TournamentAccess,
    private val teamRepository: TeamRepository,
    private val teamTokens: TeamTokenRepository,
    private val teamSessions: TeamSessionRepository,
    private val sessionTokens: SessionTokens,
    private val clock: Clock,
) : RevealTeamAccessLink, RotateTeamAccessLink, RevokeTeamAccessLink {

    override fun reveal(tournamentId: TournamentId, teamId: TeamId, owner: AdminUserId): RevealedTeamAccessLink {
        val team = requireOwnedTeam(tournamentId, teamId, owner)
        val token = requireToken(team)
        if (token.revealed) {
            throw TeamAccessLinkConflictException(
                "The access link of team ${teamId.value} was already revealed; rotate it to get a new one",
            )
        }
        if (token.isRevoked()) {
            throw TeamAccessLinkConflictException(
                "The access link of team ${teamId.value} is revoked; rotate it to get a new one",
            )
        }
        val rawToken = sessionTokens.generate()
        teamTokens.save(token.copy(tokenHash = sessionTokens.hash(rawToken), revealed = true, updatedAt = clock.instant()))
        return RevealedTeamAccessLink(teamId = team.id, tournamentId = team.tournamentId, token = rawToken)
    }

    override fun rotate(tournamentId: TournamentId, teamId: TeamId, owner: AdminUserId) {
        val team = requireOwnedTeam(tournamentId, teamId, owner)
        val token = requireToken(team)
        val rawToken = sessionTokens.generate()
        teamTokens.save(
            token.copy(
                tokenHash = sessionTokens.hash(rawToken),
                revealed = false,
                revokedAt = null,
                updatedAt = clock.instant(),
            ),
        )
        teamSessions.deleteByTeamId(team.id)
    }

    override fun revoke(tournamentId: TournamentId, teamId: TeamId, owner: AdminUserId) {
        val team = requireOwnedTeam(tournamentId, teamId, owner)
        val token = requireToken(team)
        if (!token.isRevoked()) {
            teamTokens.save(token.copy(revokedAt = clock.instant(), updatedAt = clock.instant()))
        }
        teamSessions.deleteByTeamId(team.id)
    }

    private fun requireOwnedTeam(tournamentId: TournamentId, teamId: TeamId, owner: AdminUserId): Team {
        tournamentAccess.organizedOrThrow(tournamentId, owner)
        return teamRepository.findById(teamId, tournamentId)
            ?: throw NotFoundException("Team ${teamId.value} not found in tournament ${tournamentId.value}")
    }

    private fun requireToken(team: Team): TeamToken =
        teamTokens.findByTeamId(team.id)
            ?: throw NotFoundException("Team ${team.id.value} has no access token")
}