package tichu.application.usecase

import tichu.domain.TournamentSettings
import tichu.vocabulary.InvalidFieldException

/**
 * Field rules shared by the create and update use cases, so the two verbs cannot drift
 * apart. Every failure names the offending field so the REST layer can anchor the
 * problem response to it (the frontend shows the message next to that input).
 */
internal object TournamentInputValidator {

    const val MAX_NAME_LENGTH = 255

    fun validate(name: String, maxTeams: Int, settings: TournamentSettings) {
        if (name.isBlank()) {
            throw InvalidFieldException("name", "Tournament name must not be blank")
        }
        if (name.length > MAX_NAME_LENGTH) {
            throw InvalidFieldException("name", "Tournament name must be at most $MAX_NAME_LENGTH characters")
        }
        if (maxTeams <= 0) {
            throw InvalidFieldException("maxTeams", "Tournament max teams must be a positive number")
        }
        if (settings.grandTichuBonus !in TournamentSettings.GRAND_TICHU_BONUS_OPTIONS) {
            throw InvalidFieldException(
                "grandTichuBonus",
                "Grand Tichu bonus must be one of ${TournamentSettings.GRAND_TICHU_BONUS_OPTIONS}",
            )
        }
        validateTotalRounds(settings.totalRounds)
    }

    fun validateTotalRounds(totalRounds: Int) {
        val range = TournamentSettings.TOTAL_ROUNDS_RANGE
        if (totalRounds !in range) {
            throw InvalidFieldException(
                "totalRounds",
                "Total rounds must be between ${range.first} and ${range.last}",
            )
        }
    }
}
