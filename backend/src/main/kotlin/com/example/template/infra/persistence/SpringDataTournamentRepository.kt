package com.example.template.infra.persistence

import org.springframework.data.jpa.repository.JpaRepository

/**
 * Spring Data repository for [TournamentEntity]. It is an implementation detail of
 * the JPA adapter — never exposed outside `infra/persistence`. The application-facing
 * port is [com.example.template.domain.TournamentRepository], implemented by
 * [JpaTournamentRepository].
 */
interface SpringDataTournamentRepository : JpaRepository<TournamentEntity, String> {
    fun findAllByOwnerId(ownerId: String): List<TournamentEntity>
}
