package com.example.template.application.usecase

import com.example.template.application.api.ListTournaments
import com.example.template.domain.AdminUserId
import com.example.template.domain.Tournament
import com.example.template.domain.TournamentRepository

class ListTournamentsQuery(
    private val repository: TournamentRepository,
) : ListTournaments {
    override fun list(owner: AdminUserId): List<Tournament> =
        repository.findAllByOwner(owner).sortedByDescending { it.createdAt }
}
