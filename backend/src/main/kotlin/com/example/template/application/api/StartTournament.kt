package com.example.template.application.api

import com.example.template.domain.AdminUserId
import com.example.template.domain.Round
import com.example.template.domain.Tournament
import com.example.template.domain.TournamentId

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
