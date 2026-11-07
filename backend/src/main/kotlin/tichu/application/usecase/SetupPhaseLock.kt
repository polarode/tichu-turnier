package tichu.application.usecase

import tichu.domain.Tournament
import tichu.domain.TournamentStatus
import tichu.vocabulary.TournamentNotEditableException

/**
 * The single place the Setup Phase lock is applied: settings and teams may only change
 * while the tournament is in `setup`, otherwise the verb answers
 * `409 TOURNAMENT_NOT_EDITABLE`. Call it after the ownership and existence checks, so a
 * foreign or unknown id stays a 404 in every status. Shared so the tournament and team
 * verbs cannot drift apart.
 */
internal fun Tournament.requireSetupPhase() {
    if (status != TournamentStatus.SETUP) {
        throw TournamentNotEditableException(
            "Tournament ${id.value} is locked outside setup (current status: $status)",
        )
    }
}
