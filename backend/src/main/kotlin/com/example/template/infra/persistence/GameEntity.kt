package com.example.template.infra.persistence

import com.example.template.domain.Game
import com.example.template.domain.GameParticipant
import com.example.template.domain.PlayerId
import com.example.template.domain.TeamId
import com.example.template.domain.TichuCall
import com.example.template.domain.TournamentId
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * Persistence model for [Game]. Lives in the infra layer — the domain entity
 * stays framework-free. The natural key (tournament, round number, table
 * number, game number) is enforced by the V13 schema; the surrogate id is
 * internal only. See the migration for the design rationale.
 */
@Entity
@Table(name = "games")
class GameEntity(
    /** Surrogate database id; assigned by the sequence, never by application code. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "tournament_id", nullable = false)
    val tournamentId: String,
    @Column(name = "round_number", nullable = false)
    val roundNumber: Int,
    @Column(name = "table_number", nullable = false)
    val tableNumber: Int,
    @Column(name = "game_number", nullable = false)
    val gameNumber: Int,
    @Column(name = "team1_id", nullable = false)
    val team1Id: String,
    @Column(name = "team2_id", nullable = false)
    val team2Id: String,
    @Column(name = "base_score_team1", nullable = false)
    val baseScoreTeam1: Int,
    @Column(name = "base_score_team2", nullable = false)
    val baseScoreTeam2: Int,
    @Column(name = "beschiss", nullable = false)
    val beschiss: Boolean,
    /** Optimistic-locking counter; incremented on every successful update. */
    @Column(name = "version", nullable = false)
    val version: Int,
) {
    fun toDomain(participants: List<GameParticipantEntity>): Game = Game(
        id = id,
        tournamentId = TournamentId(tournamentId),
        roundNumber = roundNumber,
        tableNumber = tableNumber,
        gameNumber = gameNumber,
        team1 = TeamId(team1Id),
        team2 = TeamId(team2Id),
        baseScoreTeam1 = baseScoreTeam1,
        baseScoreTeam2 = baseScoreTeam2,
        beschiss = beschiss,
        version = version,
        participants = participants
            .sortedBy { it.id }
            .map { it.toDomain() },
    )

    companion object {
        /** [version] is assigned by the adapter: 1 on create, +1 per update. */
        fun from(game: Game, version: Int): GameEntity = GameEntity(
            tournamentId = game.tournamentId.value,
            roundNumber = game.roundNumber,
            tableNumber = game.tableNumber,
            gameNumber = game.gameNumber,
            team1Id = game.team1.value,
            team2Id = game.team2.value,
            baseScoreTeam1 = game.baseScoreTeam1,
            baseScoreTeam2 = game.baseScoreTeam2,
            beschiss = game.beschiss,
            version = version,
        )
    }
}

/**
 * Persistence model for one player's recorded facts in a game (4 rows per game,
 * ON DELETE CASCADE with the game row). [teamId] is denormalized — the player's
 * team within this game; the use case validates it is one of the playing teams
 * before saving.
 */
@Entity
@Table(name = "game_participants")
class GameParticipantEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "game_id", nullable = false)
    val gameId: Long,
    @Column(name = "player_id", nullable = false)
    val playerId: String,
    @Column(name = "team_id", nullable = false)
    val teamId: String,
    /** Null when the player never finished (lost a double win). */
    @Column(name = "position", nullable = true)
    val position: Int?,
    @Column(name = "tichu_call", nullable = false)
    val tichuCall: String,
    @Column(name = "bomb_count", nullable = false)
    val bombCount: Int,
) {
    fun toDomain(): GameParticipant = GameParticipant(
        playerId = PlayerId(playerId),
        teamId = TeamId(teamId),
        position = position,
        call = TichuCall.valueOf(tichuCall),
        bombs = bombCount,
    )

    companion object {
        fun from(gameId: Long, participant: GameParticipant): GameParticipantEntity =
            GameParticipantEntity(
                gameId = gameId,
                playerId = participant.playerId.value,
                teamId = participant.teamId.value,
                position = participant.position,
                tichuCall = participant.call.name,
                bombCount = participant.bombs,
            )
    }
}
