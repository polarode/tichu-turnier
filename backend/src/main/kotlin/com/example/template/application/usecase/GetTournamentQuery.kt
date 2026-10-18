package com.example.template.application.usecase

import com.example.template.application.api.GetTournament
import com.example.template.domain.AdminUserId
import com.example.template.domain.Tournament
import com.example.template.domain.TournamentId
import com.example.template.domain.TournamentRepository

class GetTournamentQuery(
    private val repository: TournamentRepository,
) : GetTournament {
    override fun get(id: TournamentId, owner: AdminUserId): Tournament =
        repository.findOwnedOrThrow(id, owner)
}
