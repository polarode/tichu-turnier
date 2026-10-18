package com.example.template.application.api

import com.example.template.domain.AdminUserId
import com.example.template.domain.Tournament

/** Inbound port (driving side). Controllers depend on this, never on the implementation. */
interface CreateTournament {
    /**
     * Creates a tournament owned by [owner] — the authenticated admin, supplied by
     * the controller from the session, never from the request body.
     */
    fun create(command: TournamentWriteRequest, owner: AdminUserId): Tournament
}
