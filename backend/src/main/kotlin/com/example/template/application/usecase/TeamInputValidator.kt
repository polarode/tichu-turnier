package com.example.template.application.usecase

import com.example.template.vocabulary.InvalidFieldException

/**
 * Field rules for team creation, shared with any later verb that accepts team
 * input, so the verbs cannot drift apart. Every failure names the offending
 * field so the REST layer can anchor the problem response to it (the frontend
 * shows the message next to that input). Uniqueness rules (team name, player
 * names) are tournament-scoped and therefore live in [CreateTeamCommand], not here.
 */
internal object TeamInputValidator {

    const val MAX_NAME_LENGTH = 255

    fun validate(name: String, playerOneName: String, playerTwoName: String) {
        validateName("name", name)
        validateName("playerOneName", playerOneName)
        validateName("playerTwoName", playerTwoName)
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
