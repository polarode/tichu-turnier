package tichu.application.api

import tichu.domain.AdminUserId
import tichu.domain.TournamentId

/**
 * Inbound port (driving side) for the organizer's game correction: a last-resort
 * fix of a game whose table is already COMPLETED (ADR 0010). The
 * principal is the organizer, not a team; only the tournament's lead or a
 * co-organizer may correct, and anyone else gets not-found.
 */
interface CorrectGame {

    /**
     * Replaces the game's raw facts with [request] (which carries the version it was
     * read with), re-derives the outcome, and records who corrected it and when.
     * Fails typed with no side effects: not-found for a foreign or unknown tournament,
     * a missing round, table or game; [tichu.vocabulary.TournamentNotActiveException]
     * unless the tournament is active or completed; [tichu.vocabulary.TableNotCompletedException]
     * unless the table is completed; [tichu.vocabulary.GameConcurrentModificationException]
     * on a stale version.
     */
    fun correct(
        tournamentId: TournamentId,
        roundNumber: Int,
        tableNumber: Int,
        request: GameWriteRequest,
        admin: AdminUserId,
    ): RecordedGame
}
