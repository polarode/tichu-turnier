package com.example.template.infra.persistence

import com.example.template.domain.Game
import com.example.template.domain.GameRepository
import com.example.template.vocabulary.GameAlreadyExistsException
import com.example.template.domain.TournamentId
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * Driven adapter implementing the [GameRepository] port on top of Spring Data
 * JPA and PostgreSQL (schema managed by Flyway). A plain class — wired as a bean
 * by [PersistenceConfiguration].
 *
 * The game aggregate spans two tables: writes go through the game row first, so
 * the natural-key unique constraint or the version check rejects the whole
 * write before any participant row is touched — the use case's transaction
 * makes that atomic.
 */
// `open` only so Spring can proxy the MANDATORY-transactional guard methods (CGLIB);
// no other subclassing is intended.
open class JpaGameRepository(
    private val springDataGameRepository: SpringDataGameRepository,
    private val springDataGameParticipantRepository: SpringDataGameParticipantRepository,
) : GameRepository {

    // Note: these two methods only *require* the caller's transaction (MANDATORY);
    // they open none themselves. The @Transactional annotation exists so a
    // mis-wired call fails loudly instead of writing without atomicity — the
    // use cases are @Transactional and provide the transaction.
    @Transactional(propagation = Propagation.MANDATORY)
    override fun create(game: Game): Game {
        try {
            // Version starts at 1 on create — the domain object's version field
            // is a placeholder (0) that only PUT semantics fill in.
            val savedGame = springDataGameRepository.save(GameEntity.from(game, version = 1))
            val participants = game.participants.map {
                springDataGameParticipantRepository.save(GameParticipantEntity.from(savedGame.id, it))
            }
            return savedGame.toDomain(participants)
        } catch (ex: DataIntegrityViolationException) {
            // Only the natural-key unique constraint means "already exists" —
            // the arbiter of create races: the loser of a double-POST gets the
            // same 409 as a plain duplicate. Any other integrity violation is a
            // bug or invalid participant data and stays unmapped.
            if (ex.message?.contains("uq_games_natural_key") == true ||
                ex.cause?.message?.contains("uq_games_natural_key") == true
            ) {
                throw GameAlreadyExistsException(gameAlreadyExistsMessage(game))
            }
            throw ex
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    override fun updateWithVersion(
        tournamentId: TournamentId,
        roundNumber: Int,
        tableNumber: Int,
        gameNumber: Int,
        expectedVersion: Int,
        update: (Game) -> Game,
    ): Game? {
        val current = findEntity(tournamentId, roundNumber, tableNumber, gameNumber) ?: return null

        val updated = update(current.toDomain(loadParticipants(current.id)))
        val rows = springDataGameParticipantRepository.updateWithVersion(
            tournamentId = tournamentId.value,
            roundNumber = roundNumber,
            tableNumber = tableNumber,
            gameNumber = gameNumber,
            expectedVersion = expectedVersion,
            team1Id = updated.team1.value,
            team2Id = updated.team2.value,
            baseScoreTeam1 = updated.baseScoreTeam1,
            baseScoreTeam2 = updated.baseScoreTeam2,
            beschiss = updated.beschiss,
        )
        if (rows == 0) return null // stale version — first commit wins

        // Replace the participants wholesale, like the team aggregate does.
        springDataGameParticipantRepository.deleteAllByGameId(current.id)
        val participants = updated.participants.map {
            springDataGameParticipantRepository.save(GameParticipantEntity.from(current.id, it))
        }

        return GameEntity(
            id = current.id,
            tournamentId = current.tournamentId,
            roundNumber = current.roundNumber,
            tableNumber = current.tableNumber,
            gameNumber = current.gameNumber,
            team1Id = updated.team1.value,
            team2Id = updated.team2.value,
            baseScoreTeam1 = updated.baseScoreTeam1,
            baseScoreTeam2 = updated.baseScoreTeam2,
            beschiss = updated.beschiss,
            version = expectedVersion + 1,
        ).toDomain(participants)
    }

    override fun find(
        tournamentId: TournamentId,
        roundNumber: Int,
        tableNumber: Int,
        gameNumber: Int,
    ): Game? = findEntity(tournamentId, roundNumber, tableNumber, gameNumber)
        ?.let { it.toDomain(loadParticipants(it.id)) }

    private fun findEntity(
        tournamentId: TournamentId,
        roundNumber: Int,
        tableNumber: Int,
        gameNumber: Int,
    ): GameEntity? =
        springDataGameRepository.findByTournamentIdAndRoundNumberAndTableNumberAndGameNumber(
            tournamentId.value, roundNumber, tableNumber, gameNumber,
        )

    private fun loadParticipants(gameId: Long): List<GameParticipantEntity> =
        springDataGameParticipantRepository.findAllByGameId(gameId).sortedBy { it.id }

    private fun gameAlreadyExistsMessage(game: Game): String =
        "Game ${game.gameNumber} at table ${game.tableNumber} of round ${game.roundNumber} already exists"
}
