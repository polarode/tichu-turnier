package tichu.application.api

import tichu.domain.AdminUserId
import tichu.domain.Tournament
import tichu.domain.TournamentId

/** Inbound port (driving side). Controllers depend on this, never on the implementation. */
interface SetShuffledTables {
    /**
     * Switches Shuffled Tables (GLOSSARY.md) of the tournament owned by [owner] on or off.
     * Allowed while the tournament is in setup or active; it applies from the next round
     * that is drawn.
     *
     * Fails as not-found when [id] does not exist or is owned by another admin, and as
     * [tichu.vocabulary.TournamentNotEditableException] once the tournament is completed
     * or published.
     */
    fun set(id: TournamentId, shuffledTables: Boolean, owner: AdminUserId): Tournament
}
