package com.example.template.application.usecase

import com.example.template.domain.Team
import com.example.template.domain.TeamId
import com.example.template.vocabulary.DuplicatePlayerNameException
import com.example.template.vocabulary.DuplicateTeamNameException
import com.example.template.vocabulary.DuplicateTeamPlayersException
import com.example.template.vocabulary.InvalidFieldException

/**
 * The team validation matrix, shared by create and update so the two verbs cannot
 * drift apart. Every failure names the offending field so the REST layer can anchor
 * the problem response to it (the frontend shows the message next to that input).
 *
 * Names are compared case-insensitively after trimming. Uniqueness is tournament-scoped:
 * the caller passes the tournament's current teams, and [validateUniqueness] excludes
 * one team id so an update does not clash with itself.
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
     * Tournament-scoped uniqueness rules:
     *
     * 1. the two players of one team must be different people;
     * 2. the team name must be free among the other teams;
     * 3. each player name must be free among the other teams — a player may not sit
     *    on two teams of one tournament.
     *
     * [excludingTeamId] is the team being updated (null on create), so a team keeping
     * its own name or players is not treated as a duplicate of itself.
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
        listOf("playerOneName" to playerOneName, "playerTwoName" to playerTwoName)
            .firstOrNull { (_, playerName) ->
                otherTeams.any { team -> team.players.any { it.name.equals(playerName, ignoreCase = true) } }
            }
            ?.let { (field, playerName) ->
                throw DuplicatePlayerNameException(field, "Player \"$playerName\" is already on a team in this tournament")
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
