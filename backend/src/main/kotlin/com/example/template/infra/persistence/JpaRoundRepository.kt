package com.example.template.infra.persistence

import com.example.template.domain.Round
import com.example.template.domain.RoundRepository
import com.example.template.domain.TournamentId
import org.springframework.data.repository.findByIdOrNull

/**
 * Driven adapter implementing the [RoundRepository] port on top of Spring Data
 * JPA and PostgreSQL (schema managed by Flyway). A plain class — wired as a bean
 * by [PersistenceConfiguration].
 *
 * The round aggregate spans two tables: [save] writes the round row and its table
 * rows in the caller's transaction (the use case is `@Transactional`). Saving is
 * replace-semantics for the tables, matching the team aggregate's pattern.
 */
class JpaRoundRepository(
    private val springDataRoundRepository: SpringDataRoundRepository,
    private val springDataRoundTableRepository: SpringDataRoundTableRepository,
) : RoundRepository {

    override fun save(round: Round): Round {
        val savedRound = springDataRoundRepository.save(RoundEntity.from(round))
        // Replace semantics: re-saving a round replaces its tables, never leaving
        // stale rows behind. Runs inside the caller's transaction.
        springDataRoundTableRepository.deleteAllByRoundId(savedRound.id)
        val tables = round.pairing.tables.map {
            springDataRoundTableRepository.save(
                RoundTableEntity.from(savedRound.id, savedRound.tournamentId, it),
            )
        }
        return savedRound.toDomain(tables)
    }

    override fun findByTournamentAndNumber(tournamentId: TournamentId, number: Int): Round? =
        springDataRoundRepository.findByTournamentIdAndRoundNumber(tournamentId.value, number)
            ?.let { withTables(listOf(it)) }
            ?.firstOrNull()

    override fun findByTournament(tournamentId: TournamentId): List<Round> =
        withTables(springDataRoundRepository.findByTournamentIdOrderByRoundNumberAsc(tournamentId.value))

    /** Loads the tables of each round and reassembles the domain aggregates. */
    private fun withTables(rounds: List<RoundEntity>): List<Round> {
        val roundIds = rounds.map { it.id }
        val tablesByRound = springDataRoundTableRepository
            .findAllByRoundIdIn(roundIds)
            .sortedBy { it.tableNumber }
            .groupBy { it.roundId }
        return rounds.map { round -> round.toDomain(tablesByRound[round.id].orEmpty()) }
    }
}
