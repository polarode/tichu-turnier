package tichu.application.usecase

import tichu.domain.AdminUserId
import tichu.domain.Game
import tichu.domain.GameKey
import tichu.domain.Pairing
import tichu.domain.PlayerId
import tichu.domain.PlayerResult
import tichu.domain.Round
import tichu.domain.RoundId
import tichu.domain.Table
import tichu.domain.TableId
import tichu.domain.TableKey
import tichu.domain.TableStatus
import tichu.domain.TeamId
import tichu.domain.TeamResult
import tichu.domain.TichuCall
import tichu.domain.Tournament
import tichu.domain.TournamentId
import tichu.domain.TournamentSettings
import tichu.domain.TournamentStatus
import tichu.infra.persistence.InMemoryTournamentRepository
import tichu.testsupport.InMemoryCoOrganizerRepository
import tichu.testsupport.InMemoryGameRepository
import tichu.testsupport.InMemoryRoundRepository
import tichu.testsupport.InMemoryTableConfirmationRepository
import tichu.vocabulary.NotFoundException
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
    private val confirmationRepository = InMemoryTableConfirmationRepository()

    private val query = ListRoundsQuery(TournamentAccess(tournamentRepository, InMemoryCoOrganizerRepository()), roundRepository, gameRepository, confirmationRepository)

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
    fun `each table carries its confirmation state`() {
        confirmationRepository.add(TableKey(tournamentId, 1, 1), teamB, Instant.parse("2026-01-01T13:00:00Z"))
        confirmationRepository.add(TableKey(tournamentId, 1, 2), teamD, Instant.parse("2026-01-01T13:00:00Z"))
        confirmationRepository.add(TableKey(tournamentId, 1, 2), teamC, Instant.parse("2026-01-01T13:01:00Z"))

        val (table1, table2) = query.list(tournamentId, owner).single().tables

        assertEquals(TableStatus.CONFIRMING, table1.confirmation.status)
        assertEquals(listOf(teamB), table1.confirmation.confirmedTeamIds)
        assertEquals(TableStatus.COMPLETED, table2.confirmation.status)
        assertEquals(listOf(teamC, teamD), table2.confirmation.confirmedTeamIds, "seat order")
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
