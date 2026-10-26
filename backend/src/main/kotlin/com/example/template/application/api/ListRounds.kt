package com.example.template.application.api

import com.example.template.domain.AdminUserId
import com.example.template.domain.Round
import com.example.template.domain.TournamentId

interface ListRounds {
    /** Fails as not-found when the tournament does not exist or is owned by another admin. */
    fun list(tournamentId: TournamentId, owner: AdminUserId): List<Round>
}
