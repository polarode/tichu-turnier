package tichu.application.api

import tichu.domain.AdminUserId
import tichu.domain.Tournament
import tichu.domain.TournamentId

/** Inbound port (driving side). Controllers depend on this, never on the implementation. */
interface PublishTournament {
    /**
     * Publishes the completed tournament owned by [owner], moving it to `published`.
     * Fails typed (no side effects) as not-found for a foreign or unknown tournament,
     * and as [tichu.vocabulary.TournamentNotCompletedException] unless completed, which
     * includes a tournament that is already published (publishing is one-way).
     */
    fun publish(tournamentId: TournamentId, owner: AdminUserId): Tournament
}
