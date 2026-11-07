package tichu.application.api

import tichu.domain.TournamentId

interface CheckPublicTournamentVisible {
    /**
     * Passes silently when the tournament is visible to spectators (public and in a publicly
     * listable status), the same rule [GetPublicTournament] applies. Loads no pairings, so it
     * is the cheap gate for public endpoints that need only visibility.
     *
     * Throws `NotFoundException` otherwise. Setup, non-public and unknown ids look alike.
     */
    fun check(id: TournamentId)
}
