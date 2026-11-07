package tichu.application.usecase

import tichu.application.api.CreateTeam
import tichu.application.api.TeamWriteRequest
import tichu.domain.AdminUserId
import tichu.domain.IdGenerator
import tichu.domain.Player
import tichu.domain.PlayerId
import tichu.domain.SessionTokens
import tichu.domain.Team
import tichu.domain.TeamId
import tichu.domain.TeamRepository
import tichu.domain.TeamToken
import tichu.domain.TeamTokenRepository
import tichu.domain.TournamentId
import tichu.domain.TournamentRepository
import tichu.domain.TournamentStatus
import tichu.vocabulary.TeamLimitReachedException
import tichu.vocabulary.TournamentNotEditableException
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * Creates a team (name + exactly two named players) in the caller's tournament.
 *
 * All rules are enforced here so the validation matrix is testable at the
 * use-case seam without Spring:
 *
 * 1. ownership: a foreign or missing tournament id fails as not-found (`findOwnedOrThrow`);
 * 2. setup lock: the tournament must still be in `setup`, otherwise it answers
 *    `409 TOURNAMENT_NOT_EDITABLE` (typed, [TournamentNotEditableException]);
 * 3. field rules: blank / overlong names (see [TeamInputValidator]);
 * 4. capacity: the tournament must be below its configured `max_teams`;
 * 5. the two players of one team must be different people (typed, field-anchored);
 * 6. the team name must be free within the tournament (typed, field-anchored);
 * 7. each player name must be free within the tournament — a player may not sit on
 *    two teams of one tournament (typed, field-anchored).
 *
 * The field and uniqueness matrix is delegated to [TeamInputValidator] so it stays
 * identical to the update path. Names are compared case-insensitively after trimming,
 * mirroring how emails are matched for uniqueness. Identical team/player names in
 * *different* tournaments of the same organizer are accepted because every check is
 * scoped to one tournament id.
 *
 * Saving touches several tables (teams + players + the team's access token), so the
 * use case is transactional: the team and its token are provisioned together and a
 * failure cannot leave one without the other.
 */
@Transactional
class CreateTeamCommand(
    private val tournamentRepository: TournamentRepository,
    private val teamRepository: TeamRepository,
    private val teamTokenRepository: TeamTokenRepository,
    private val sessionTokens: SessionTokens,
    private val idGenerator: IdGenerator,
    private val clock: Clock,
) : CreateTeam {

    override fun create(tournamentId: TournamentId, command: TeamWriteRequest, owner: AdminUserId): Team {
        val tournament = tournamentRepository.findOwnedOrThrow(tournamentId, owner)
        if (tournament.status != TournamentStatus.SETUP) {
            throw TournamentNotEditableException(
                "Tournament ${tournamentId.value} can only be edited while in setup " +
                    "(current status: ${tournament.status})",
            )
        }

        val name = command.name.trim()
        val playerOneName = command.playerOneName.trim()
        val playerTwoName = command.playerTwoName.trim()
        TeamInputValidator.validate(name, playerOneName, playerTwoName)

        val existingTeams = teamRepository.findAllByTournament(tournamentId)
        if (existingTeams.size >= tournament.maxTeams) {
            throw TeamLimitReachedException(
                "Tournament \"${tournament.name}\" already holds its maximum of ${tournament.maxTeams} teams",
            )
        }
        TeamInputValidator.validateUniqueness(existingTeams, name, playerOneName, playerTwoName)

        val teamId = TeamId(idGenerator.newId())
        val playerOne = Player(
            id = PlayerId(idGenerator.newId()),
            tournamentId = tournamentId,
            teamId = teamId,
            name = playerOneName,
        )
        val playerTwo = Player(
            id = PlayerId(idGenerator.newId()),
            tournamentId = tournamentId,
            teamId = teamId,
            name = playerTwoName,
        )
        val savedTeam = teamRepository.save(
            Team(
                id = teamId,
                tournamentId = tournamentId,
                name = name,
                players = listOf(playerOne, playerTwo),
            ),
        )

        // Auto-provision the team access token in the same transaction. The raw
        // token is generated, hashed and discarded: it is never persisted, never
        // returned by this (or any) response — the organizer reveals it later
        // through the dedicated reveal flow (#25).
        val now = clock.instant()
        teamTokenRepository.save(
            TeamToken(
                teamId = teamId,
                tokenHash = sessionTokens.hash(sessionTokens.generate()),
                revealed = false,
                createdAt = now,
                updatedAt = now,
            ),
        )
        return savedTeam
    }
}
