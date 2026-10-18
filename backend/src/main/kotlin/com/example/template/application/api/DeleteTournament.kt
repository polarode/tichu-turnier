package com.example.template.application.api

import com.example.template.domain.AdminUserId
import com.example.template.domain.TournamentId

/** Inbound port (driving side). Controllers depend on this, never on the implementation. */
interface DeleteTournament {
    /**
     * Deletes the tournament owned by [owner] together with everything scoped to it;
     * child tables cascade from `tournaments`. Fails as not-found when [id] does not
     * exist or is owned by another admin.
     */
    fun delete(id: TournamentId, owner: AdminUserId)
}
