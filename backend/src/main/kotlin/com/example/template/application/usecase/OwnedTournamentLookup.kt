package com.example.template.application.usecase

import com.example.template.domain.AdminUserId
import com.example.template.domain.Tournament
import com.example.template.domain.TournamentId
import com.example.template.domain.TournamentRepository
import com.example.template.vocabulary.NotFoundException

/**
 * The single place the ownership rule is applied on a single resource: load the
 * tournament and fail exactly as if it did not exist when another admin owns it.
 * Returning not-found (rather than forbidden) means a foreign admin cannot even
 * learn that the id exists. Shared so every later verb (edit, delete, start, …)
 * cannot drift apart.
 */
internal fun TournamentRepository.findOwnedOrThrow(id: TournamentId, owner: AdminUserId): Tournament {
    val tournament = findById(id)
    if (tournament == null || !tournament.isOwnedBy(owner)) {
        throw NotFoundException("Tournament ${id.value} not found")
    }
    return tournament
}
