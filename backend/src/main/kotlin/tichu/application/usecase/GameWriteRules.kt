package tichu.application.usecase

import tichu.application.api.GameWriteRequest
import tichu.domain.Table
import tichu.domain.TeamId
import tichu.domain.TeamRepository
import tichu.domain.TeamResult
import tichu.domain.PlayerResult
import tichu.domain.TournamentId
import tichu.vocabulary.GameValidationException
import tichu.vocabulary.InvalidFieldException

/*
 * The raw-facts rules every game write shares: the team-recording use case
 * (RecordGameCommand) and the organizer's correction (CorrectGameCommand) validate
 * a submitted game the same way, so the rules live here once.
 */

/** A version is sent on an edit and only on an edit; a create must not carry one. */
internal fun requireVersionOnUpdate(create: Boolean, request: GameWriteRequest) {
    if (create && request.version != null) {
        throw InvalidFieldException("version", "Version is only sent when editing an existing game")
    }
    if (!create && request.version == null) {
        throw InvalidFieldException("version", "Editing a game requires the version it was read with")
    }
}

/** The game number must be one the table plays: 1–4, or 1–6 at a trio table. */
internal fun requireGameNumber(gameNumber: Int, table: Table) {
    if (gameNumber !in 1..table.gameCount) {
        throw InvalidFieldException("gameNumber", "Game number must be between 1 and ${table.gameCount}")
    }
}

/** The submitted facts, team-grouped, as the scoring engine reads them. */
internal fun toTeamResults(request: GameWriteRequest): List<TeamResult> =
    request.teams.map { team ->
        TeamResult(
            teamId = team.teamId,
            baseScore = team.baseScore,
            players = team.players.map { PlayerResult(it.playerId, it.position, it.call, it.bombs) },
        )
    }

/**
 * The two submitted teams must be the game's playing teams, and every participant
 * must belong to the team it is grouped under.
 */
internal fun requirePlayingTeams(
    teamRepository: TeamRepository,
    tournamentId: TournamentId,
    teamResults: List<TeamResult>,
    playingTeams: List<TeamId>,
) {
    if (teamResults.size != 2) {
        throw GameValidationException("teams", "A game has exactly 2 playing teams, got ${teamResults.size}")
    }
    if (teamResults.map { it.teamId }.toSet() != playingTeams.toSet()) {
        throw GameValidationException(
            "teams",
            "The submitted teams must be the table's playing teams ${playingTeams.map { it.value }}",
        )
    }
    // The participants must be exactly the playing teams' players: each
    // submitted player has to belong to the team it is grouped under.
    for (team in teamResults) {
        val roster = teamRepository.findById(team.teamId, tournamentId)
            ?: throw GameValidationException("teams", "Team ${team.teamId.value} is unknown in this tournament")
        val rosterIds = roster.players.map { it.id }.toSet()
        if (team.players.any { it.playerId !in rosterIds }) {
            throw GameValidationException(
                "players",
                "All participants must be players of team ${team.teamId.value}",
            )
        }
    }
}
