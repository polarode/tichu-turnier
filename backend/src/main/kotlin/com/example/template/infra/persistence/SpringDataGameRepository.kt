package com.example.template.infra.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional

/**
 * Spring Data repositories for [GameEntity] / [GameParticipantEntity]. They are
 * implementation details of the JPA adapter — never exposed outside
 * `infra/persistence`. The application-facing port is
 * [com.example.template.domain.GameRepository], implemented by [JpaGameRepository].
 */
interface SpringDataGameRepository : JpaRepository<GameEntity, Long> {
    fun findByTournamentIdAndRoundNumberAndTableNumberAndGameNumber(
        tournamentId: String,
        roundNumber: Int,
        tableNumber: Int,
        gameNumber: Int,
    ): GameEntity?
}

interface SpringDataGameParticipantRepository : JpaRepository<GameParticipantEntity, Long> {
    fun findAllByGameId(gameId: Long): List<GameParticipantEntity>

    /** Derived deletes run outside `SimpleJpaRepository`, so they carry their own transaction. */
    @Transactional
    fun deleteAllByGameId(gameId: Long)

    /**
     * The optimistic-locking write: bumps the version and applies the new facts
     * only when the stored row still carries [expectedVersion]. Runs as a bulk
     * JPQL update bypassing the persistence context — rows-updated 0 means the
     * caller lost the race.
     */
    @Modifying
    @Query(
        """
        UPDATE GameEntity g
        SET g.team1Id = :team1Id, g.team2Id = :team2Id,
            g.baseScoreTeam1 = :baseScoreTeam1, g.baseScoreTeam2 = :baseScoreTeam2,
            g.beschiss = :beschiss, g.version = g.version + 1
        WHERE g.tournamentId = :tournamentId
          AND g.roundNumber = :roundNumber
          AND g.tableNumber = :tableNumber
          AND g.gameNumber = :gameNumber
          AND g.version = :expectedVersion
        """,
    )
    fun updateWithVersion(
        @Param("tournamentId") tournamentId: String,
        @Param("roundNumber") roundNumber: Int,
        @Param("tableNumber") tableNumber: Int,
        @Param("gameNumber") gameNumber: Int,
        @Param("expectedVersion") expectedVersion: Int,
        @Param("team1Id") team1Id: String,
        @Param("team2Id") team2Id: String,
        @Param("baseScoreTeam1") baseScoreTeam1: Int,
        @Param("baseScoreTeam2") baseScoreTeam2: Int,
        @Param("beschiss") beschiss: Boolean,
    ): Int
}
