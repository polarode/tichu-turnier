package com.example.template.application.usecase

import com.example.template.application.api.ListRounds
import com.example.template.domain.AdminUserId
import com.example.template.domain.Round
import com.example.template.domain.RoundRepository
import com.example.template.domain.TournamentId
import com.example.template.domain.TournamentRepository

class ListRoundsQuery(
    private val tournamentRepository: TournamentRepository,
    private val roundRepository: RoundRepository,
) : ListRounds {
    override fun list(tournamentId: TournamentId, owner: AdminUserId): List<Round> {
        tournamentRepository.findOwnedOrThrow(tournamentId, owner)
        return roundRepository.findByTournament(tournamentId)
    }
}
