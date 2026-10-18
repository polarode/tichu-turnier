package com.example.template.application.usecase

import com.example.template.application.api.CreateTeam
import com.example.template.application.api.TeamWriteRequest
import com.example.template.domain.AdminUserId
import com.example.template.domain.IdGenerator
import com.example.template.domain.Player
import com.example.template.domain.PlayerId
import com.example.template.domain.Team
import com.example.template.domain.TeamId
import com.example.template.domain.TeamRepository
import com.example.template.domain.TournamentId
import com.example.template.domain.TournamentRepository
import com.example.template.vocabulary.TeamLimitReachedException
import org.springframework.transaction.annotation.Transactional

/**
 * Creates a team (name + exactly two named players) in the caller's tournament.
 *
 * All rules are enforced here so the validation matrix is testable at the
 * use-case seam without Spring:
 *
 * 1. ownership: a foreign or missing tournament id fails as not-found (`findOwnedOrThrow`);
 * 2. field rules: blank / overlong names (see [TeamInputValidator]);
 * 3. capacity: the tournament must be below its configured `max_teams`;
 * 4. the two players of one team must be different people (typed, field-anchored);
 * 5. the team name must be free within the tournament (typed, field-anchored);
 * 6. each player name must be free within the tournament — a player may not sit on
 *    two teams of one tournament (typed, field-anchored).
 *
 * The field and uniqueness matrix is delegated to [TeamInputValidator] so it stays
 * identical to the update path. Names are compared case-insensitively after trimming,
 * mirroring how emails are matched for uniqueness. Identical team/player names in
 * *different* tournaments of the same organizer are accepted because every check is
 * scoped to one tournament id.
 *
 * Saving touches two tables (teams + players), so the use case is transactional.
 */
@Transactional
class CreateTeamCommand(
    private val tournamentRepository: TournamentRepository,
    private val teamRepository: TeamRepository,
    private val idGenerator: IdGenerator,
) : CreateTeam {

    override fun create(tournamentId: TournamentId, command: TeamWriteRequest, owner: AdminUserId): Team {
        val tournament = tournamentRepository.findOwnedOrThrow(tournamentId, owner)

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
        return teamRepository.save(
            Team(
                id = teamId,
                tournamentId = tournamentId,
                name = name,
                players = listOf(playerOne, playerTwo),
            ),
        )
    }
}
