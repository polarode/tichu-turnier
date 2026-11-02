package tichu.infra.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.transaction.annotation.Transactional

/**
 * Spring Data repository for [TableConfirmationEntity]; an implementation detail
 * of [JpaTableConfirmationRepository], never exposed outside `infra/persistence`.
 */
interface SpringDataTableConfirmationRepository : JpaRepository<TableConfirmationEntity, Long> {
    fun findAllByTournamentIdAndRoundNumberAndTableNumber(
        tournamentId: String,
        roundNumber: Int,
        tableNumber: Int,
    ): List<TableConfirmationEntity>

    fun findAllByTournamentIdAndRoundNumber(tournamentId: String, roundNumber: Int): List<TableConfirmationEntity>

    /** Derived deletes run outside `SimpleJpaRepository`, so they carry their own transaction. */
    @Transactional
    fun deleteByTournamentIdAndRoundNumberAndTableNumberAndTeamId(
        tournamentId: String,
        roundNumber: Int,
        tableNumber: Int,
        teamId: String,
    )
}
