package tichu.infra.persistence

import tichu.domain.AdminUserId
import tichu.domain.Tournament
import tichu.domain.TournamentId
import tichu.domain.TournamentRepository
import tichu.domain.TournamentStatus
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * Driven adapter implementing the [TournamentRepository] port on top of Spring Data
 * JPA and PostgreSQL (schema managed by Flyway). A plain class — wired as a bean by
 * [PersistenceConfiguration].
 */
// `open` only so Spring can proxy the MANDATORY-transactional lock (CGLIB).
open class JpaTournamentRepository(
    private val springDataTournamentRepository: SpringDataTournamentRepository,
) : TournamentRepository {

    override fun save(tournament: Tournament): Tournament =
        springDataTournamentRepository.save(TournamentEntity.from(tournament)).toDomain()

    override fun findById(id: TournamentId): Tournament? =
        springDataTournamentRepository.findById(id.value).map { it.toDomain() }.orElse(null)

    // MANDATORY: a row lock outside a transaction would be released immediately,
    // so a mis-wired call fails loudly instead of silently not serializing.
    @Transactional(propagation = Propagation.MANDATORY)
    override fun lock(id: TournamentId) {
        springDataTournamentRepository.lock(id.value)
    }

    override fun findAllByOwner(owner: AdminUserId): List<Tournament> =
        springDataTournamentRepository.findAllByOwnerId(owner.value).map { it.toDomain() }

    override fun findAllPublic(): List<Tournament> =
        springDataTournamentRepository
            .findByPublicVisibilityTrueAndStatusIn(TournamentStatus.PUBLICLY_LISTABLE)
            .map { it.toDomain() }

    override fun delete(id: TournamentId) {
        springDataTournamentRepository.deleteById(id.value)
    }
}
