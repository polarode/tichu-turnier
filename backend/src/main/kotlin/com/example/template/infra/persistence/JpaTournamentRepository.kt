package com.example.template.infra.persistence

import com.example.template.domain.AdminUserId
import com.example.template.domain.Tournament
import com.example.template.domain.TournamentId
import com.example.template.domain.TournamentRepository

/**
 * Driven adapter implementing the [TournamentRepository] port on top of Spring Data
 * JPA and PostgreSQL (schema managed by Flyway). A plain class — wired as a bean by
 * [PersistenceConfiguration].
 */
class JpaTournamentRepository(
    private val springDataTournamentRepository: SpringDataTournamentRepository,
) : TournamentRepository {

    override fun save(tournament: Tournament): Tournament =
        springDataTournamentRepository.save(TournamentEntity.from(tournament)).toDomain()

    override fun findById(id: TournamentId): Tournament? =
        springDataTournamentRepository.findById(id.value).map { it.toDomain() }.orElse(null)

    override fun findAllByOwner(owner: AdminUserId): List<Tournament> =
        springDataTournamentRepository.findAllByOwnerId(owner.value).map { it.toDomain() }
}
