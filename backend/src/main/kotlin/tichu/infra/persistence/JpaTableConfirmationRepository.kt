package tichu.infra.persistence

import tichu.domain.TableConfirmationRepository
import tichu.domain.TableId
import tichu.domain.TableKey
import tichu.domain.TeamId
import tichu.domain.TournamentId
import java.time.Instant

/**
 * Driven adapter implementing the [TableConfirmationRepository] port on top of
 * Spring Data JPA and PostgreSQL (schema managed by Flyway). A plain class —
 * wired as a bean by [PersistenceConfiguration]. Writes join the caller's
 * transaction, which holds the round table's lock (ADR 0006).
 */
class JpaTableConfirmationRepository(
    private val springDataRepository: SpringDataTableConfirmationRepository,
) : TableConfirmationRepository {

    override fun findByTable(key: TableKey): Set<TeamId> =
        springDataRepository
            .findAllByTournamentIdAndRoundNumberAndTableNumber(key.tournamentId.value, key.roundNumber, key.tableNumber)
            .map { TeamId(it.teamId) }
            .toSet()

    override fun findByRound(tournamentId: TournamentId, roundNumber: Int): Map<TableId, Set<TeamId>> =
        springDataRepository.findAllByTournamentIdAndRoundNumber(tournamentId.value, roundNumber)
            .groupBy({ TableId(it.tableNumber) }, { TeamId(it.teamId) })
            .mapValues { (_, teams) -> teams.toSet() }

    override fun add(key: TableKey, teamId: TeamId, confirmedAt: Instant) {
        springDataRepository.save(
            TableConfirmationEntity(
                tournamentId = key.tournamentId.value,
                roundNumber = key.roundNumber,
                tableNumber = key.tableNumber,
                teamId = teamId.value,
                confirmedAt = confirmedAt,
            ),
        )
    }

    override fun remove(key: TableKey, teamId: TeamId) {
        springDataRepository.deleteByTournamentIdAndRoundNumberAndTableNumberAndTeamId(
            key.tournamentId.value, key.roundNumber, key.tableNumber, teamId.value,
        )
    }
}
