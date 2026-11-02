package com.example.template.application.usecase

import com.example.template.domain.AdminUserId
import com.example.template.domain.Game
import com.example.template.domain.GameKey
import com.example.template.domain.Pairing
import com.example.template.domain.PlayerId
import com.example.template.domain.PlayerResult
import com.example.template.domain.Round
import com.example.template.domain.RoundId
import com.example.template.domain.Table
import com.example.template.domain.TableId
import com.example.template.domain.TeamId
import com.example.template.domain.TeamResult
import com.example.template.domain.TichuCall
import com.example.template.domain.Tournament
import com.example.template.domain.TournamentId
import com.example.template.domain.TournamentSettings
import com.example.template.domain.TournamentStatus
import com.example.template.infra.persistence.InMemoryTournamentRepository
import com.example.template.testsupport.InMemoryGameRepository
import com.example.template.testsupport.InMemoryRoundRepository
import com.example.template.vocabulary.NotFoundException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Use-case tests for the admin's rounds read (S5d, #102): every round with
 * each table's entered games, scored server-side, scoped to the owning admin.
 */
class ListRoundsQueryTest {

    private val tournamentId = TournamentId("t-1")
    private val owner = AdminUserId("admin-owner")
    private val teamA = TeamId("team-a")
    private val teamB = TeamId("team-b")
    private val teamC = TeamId("team-c")
    private val teamD = TeamId("team-d")

    private val tournamentRepository = InMemoryTournamentRepository()
    private val roundRepository = InMemoryRoundRepository()
    private val gameRepository = InMemoryGameRepository()

    private val query = ListRoundsQuery(tournamentRepository, roundRepository, gameRepository)

    @BeforeEach
    fun setUp() {
        tournamentRepository.save(
            Tournament(
                id = tournamentId,
                ownerId = owner,
                name = "Spring Classic",
                description = "",
                maxTeams = 16,
                settings = TournamentSettings(),
                status = TournamentStatus.ACTIVE,
                createdAt = Instant.parse("2026-01-01T12:00:00Z"),
                updatedAt = Instant.parse("2026-01-01T12:00:00Z"),
            ),
        )
        roundRepository.save(
            Round(
                id = RoundId("r-1"),
                tournamentId = tournamentId,
                number = 1,
                pairing = Pairing(listOf(Table(TableId(1), listOf(teamA, teamB)), Table(TableId(2), listOf(teamC, teamD)))),
            ),
        )
    }

    /** Team A takes 55 base and a successful small Tichu: 155 TP against team B's 45. */
    private fun seedGame(gameNumber: Int, beschiss: Boolean = false, roundNumber: Int = 1) {
        gameRepository.create(
            Game.record(
                GameKey(tournamentId, roundNumber = roundNumber, tableNumber = 1, gameNumber = gameNumber),
                listOf(
                    TeamResult(teamA, 55, listOf(
                        PlayerResult(PlayerId("a1"), 1, TichuCall.SMALL, 0),
                        PlayerResult(PlayerId("a2"), 3, TichuCall.NONE, 0),
                    )),
                    TeamResult(teamB, 45, listOf(
                        PlayerResult(PlayerId("b1"), 2, TichuCall.NONE, 0),
                        PlayerResult(PlayerId("b2"), 4, TichuCall.NONE, 0),
                    )),
                ),
                beschiss,
                TournamentSettings(),
            ),
        )
    }

    @Test
    fun `each table carries its entered games in game-number order with the derived outcome`() {
        seedGame(2, beschiss = true)
        seedGame(1)

        val (round) = query.list(tournamentId, owner)
        val (table1, table2) = round.tables

        assertEquals(1, round.round.number)
        assertEquals(TableId(1), table1.table.id)
        assertEquals(listOf(1, 2), table1.games.map { it.gameNumber })
        assertEquals(mapOf(teamA to 155, teamB to 45), table1.games[0].outcome.teamPoints)
        assertEquals(listOf(false, true), table1.games.map { it.beschiss })
        assertTrue(table2.games.isEmpty(), "a table without entries has no games")
    }

    @Test
    fun `every round lists only its own games`() {
        roundRepository.save(
            Round(
                id = RoundId("r-2"),
                tournamentId = tournamentId,
                number = 2,
                pairing = Pairing(listOf(Table(TableId(1), listOf(teamA, teamB)), Table(TableId(2), listOf(teamC, teamD)))),
            ),
        )
        seedGame(1, roundNumber = 1)
        seedGame(3, roundNumber = 2)

        val (round1, round2) = query.list(tournamentId, owner).sortedBy { it.round.number }

        assertEquals(listOf(1), round1.tables[0].games.map { it.gameNumber })
        assertEquals(listOf(3), round2.tables[0].games.map { it.gameNumber })
    }

    @Test
    fun `rounds of a tournament owned by another admin are not found`() {
        assertFailsWith<NotFoundException> { query.list(tournamentId, AdminUserId("someone-else")) }
    }
}
