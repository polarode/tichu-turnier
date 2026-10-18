package com.example.template.application.usecase

import com.example.template.vocabulary.InvalidFieldException

/**
 * Field rules shared by the create and update use cases, so the two verbs cannot drift
 * apart. Every failure names the offending field so the REST layer can anchor the
 * problem response to it (the frontend shows the message next to that input).
 */
internal object TournamentInputValidator {

    const val MAX_NAME_LENGTH = 255

    fun validate(name: String, maxTeams: Int) {
        if (name.isBlank()) {
            throw InvalidFieldException("name", "Tournament name must not be blank")
        }
        if (name.length > MAX_NAME_LENGTH) {
            throw InvalidFieldException("name", "Tournament name must be at most $MAX_NAME_LENGTH characters")
        }
        if (maxTeams <= 0) {
            throw InvalidFieldException("maxTeams", "Tournament max teams must be a positive number")
        }
    }
}
