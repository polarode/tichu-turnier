package tichu.application.usecase

import tichu.domain.Team
import tichu.domain.TeamId
import tichu.vocabulary.DuplicateTeamNameException
import tichu.vocabulary.DuplicateTeamPlayersException
import tichu.vocabulary.InvalidFieldException

/**
 * The team validation matrix, shared by create and update so the two verbs cannot
 * drift apart. Every failure names the offending field so the REST layer can anchor
 * the problem response to it (the frontend shows the message next to that input).
 *
 * Names are compared case-insensitively after trimming. Team names are unique per
 * tournament: the caller passes the tournament's current teams, and [validateUniqueness]
 * excludes one team id so an update does not clash with itself. Player names are only
 * unique within their own team.
 */
internal object TeamInputValidator {

    const val MAX_NAME_LENGTH = 255

    /** Field rules only — no knowledge of other teams. */
    fun validate(name: String, playerOneName: String, playerTwoName: String) {
        validateName("name", name)
        validateName("playerOneName", playerOneName)
        validateName("playerTwoName", playerTwoName)
    }

    /**
     * Uniqueness rules:
     *
     * 1. the two players of one team must be different people (within the team only);
     * 2. the team name must be free among the other teams of the tournament.
     *
     * Player names are not checked against other teams: the same name may appear on
     * several teams of one tournament.
     *
     * [excludingTeamId] is the team being updated (null on create), so a team keeping
     * its own name is not treated as a duplicate of itself.
     */
    fun validateUniqueness(
        teams: List<Team>,
        name: String,
        playerOneName: String,
        playerTwoName: String,
        excludingTeamId: TeamId? = null,
    ) {
        val otherTeams = teams.filter { it.id != excludingTeamId }

        if (playerOneName.equals(playerTwoName, ignoreCase = true)) {
            throw DuplicateTeamPlayersException(
                "playerTwoName",
                "The two players of a team must be different people",
            )
        }
        if (otherTeams.any { it.name.equals(name, ignoreCase = true) }) {
            throw DuplicateTeamNameException("name", "Team name \"$name\" is already taken in this tournament")
        }
    }

    private fun validateName(field: String, value: String) {
        if (value.isBlank()) {
            throw InvalidFieldException(field, "Name must not be blank")
        }
        if (value.length > MAX_NAME_LENGTH) {
            throw InvalidFieldException(field, "Name must be at most $MAX_NAME_LENGTH characters")
        }
    }
}
