package com.example.template.application.api

import com.example.template.domain.AdminUserId
import com.example.template.domain.Tournament
import com.example.template.domain.TournamentId

interface GetTournament {
    /** Fails as not-found when [id] does not exist or is owned by another admin. */
    fun get(id: TournamentId, owner: AdminUserId): Tournament
}
