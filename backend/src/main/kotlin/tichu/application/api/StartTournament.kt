package tichu.application.api

import tichu.domain.AdminUserId
import tichu.domain.Round
import tichu.domain.Tournament
import tichu.domain.TournamentId

/**
 * Inbound port (driving side). Controllers depend on this, never on the implementation.
 */
interface StartTournament {

    /**
     * Starts the tournament owned by [owner] — transitions it to active and creates
     * round 1 with its table assignment. Fails typed (no side effects) when the
     * tournament is not in setup, holds fewer than two teams, or cannot be paired.
     */
    fun start(tournamentId: TournamentId, owner: AdminUserId): StartedTournament
}

/** The started tournament together with the freshly created first round. */
data class StartedTournament(
    val tournament: Tournament,
    val round: Round,
)
