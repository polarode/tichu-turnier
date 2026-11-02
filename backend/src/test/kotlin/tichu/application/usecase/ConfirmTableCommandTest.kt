package tichu.application.usecase

import tichu.application.api.AuthenticatedTeam
import tichu.application.api.ConfirmTable
import tichu.application.api.GameWriteRequest
import tichu.application.api.PlayerResultInput
import tichu.application.api.RecordGame
import tichu.application.api.SeenGame
import tichu.application.api.TeamResultInput
import tichu.domain.AdminUserId
import tichu.domain.Pairing
import tichu.domain.Player
import tichu.domain.PlayerId
import tichu.domain.Round
import tichu.domain.RoundId
import tichu.domain.Table
import tichu.domain.TableId
import tichu.domain.TableKey
import tichu.domain.TableStatus
import tichu.domain.Team
import tichu.domain.TeamId
import tichu.domain.TichuCall
import tichu.domain.Tournament
import tichu.domain.TournamentId
import tichu.domain.TournamentSettings
import tichu.domain.TournamentStatus
import tichu.infra.persistence.InMemoryTournamentRepository
import tichu.testsupport.InMemoryGameRepository
import tichu.testsupport.InMemoryRoundRepository
import tichu.testsupport.InMemoryTableConfirmationRepository
import tichu.testsupport.InMemoryTeamRepository
import tichu.testsupport.MutableClock
import tichu.vocabulary.ForbiddenException
import tichu.vocabulary.GameConcurrentModificationException
import tichu.vocabulary.InvalidFieldException
import tichu.vocabulary.NotFoundException
import tichu.vocabulary.TableCompletedException
import tichu.vocabulary.TableIncompleteException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Use-case unit tests for confirm/retract (S6a, #105; ADR 0006) at the inbound-port
 * seam with in-memory doubles. Games are entered through [RecordGame], the real
 * write path, so their versions are the ones a team would have read.
 */
class ConfirmTableCommandTest {

    private val tournamentId = TournamentId("t-1")
    private val teamA = TeamId("team-a")
    private val teamB = TeamId("team-b")
    private val teamC = TeamId("team-c")
    private val tableKey = TableKey(tournamentId, 1, 1)

    private val tournamentRepository = InMemoryTournamentRepository()
    private val teamRepository = InMemoryTeamRepository()
    private val roundRepository = InMemoryRoundRepository()
    private val gameRepository = InMemoryGameRepository()
    private val confirmationRepository = InMemoryTableConfirmationRepository()
    private val clock = MutableClock(Instant.parse("2026-01-01T12:00:00Z"))

    private val recordGame: RecordGame =
        RecordGameCommand(tournamentRepository, roundRepository, teamRepository, gameRepository, confirmationRepository)
    private val confirmTable: ConfirmTable =
        ConfirmTableCommand(roundRepository, gameRepository, confirmationRepository, clock)

    @BeforeEach
    fun setUp() {
        tournamentRepository.save(
            Tournament(
                id = tournamentId,
                ownerId = AdminUserId("admin-owner"),
                name = "Spring Classic",
                description = "",
                maxTeams = 16,
                settings = TournamentSettings(),
                status = TournamentStatus.ACTIVE,
                createdAt = Instant.parse("2026-01-01T12:00:00Z"),
                updatedAt = Instant.parse("2026-01-01T12:00:00Z"),
            ),
        )
        listOf(teamA, teamB, teamC).forEach { teamRepository.save(team(it)) }
        seat(teamA, teamB)
    }

    private fun team(id: TeamId) = Team(
        id = id,
        tournamentId = tournamentId,
        name = "Team ${id.value}",
        players = listOf(
            Player(PlayerId("${id.value}-p1"), tournamentId, id, "P1"),
            Player(PlayerId("${id.value}-p2"), tournamentId, id, "P2"),
        ),
    )

    private fun seat(first: TeamId, second: TeamId, third: TeamId? = null) {
        roundRepository.save(
            Round(RoundId("r-1"), tournamentId, 1, Pairing(listOf(Table(TableId(1), listOfNotNull(first, second, third))))),
        )
    }

    private fun principal(teamId: TeamId) = AuthenticatedTeam(teamId, tournamentId)

    /** Enters game [gameNumber] for its playing teams through the real write path. */
    private fun enterGame(gameNumber: Int) {
        val table = roundRepository.findByTournamentAndNumber(tournamentId, 1)!!.table(1)!!
        val (first, second) = table.playingTeams(gameNumber)
        recordGame.create(tournamentId, 1, 1, request(gameNumber, first, second), principal(first))
    }

    private fun request(gameNumber: Int, first: TeamId, second: TeamId, version: Int? = null) = GameWriteRequest(
        gameNumber = gameNumber,
        beschiss = false,
        version = version,
        teams = listOf(first, second).mapIndexed { i, id ->
            TeamResultInput(
                teamId = id,
                baseScore = if (i == 0) 60 else 40,
                players = listOf(
                    PlayerResultInput(PlayerId("${id.value}-p1"), 1 + i, TichuCall.NONE, 0),
                    PlayerResultInput(PlayerId("${id.value}-p2"), 3 + i, TichuCall.NONE, 0),
                ),
            )
        },
    )

    private fun enterAll(count: Int) = (1..count).forEach(::enterGame)

    /** The versions as stored now: what a team that just reloaded has seen. */
    private fun seen(count: Int) = (1..count).map { SeenGame(it, gameRepository.find(tableKey.game(it))!!.version) }

    private fun TableKey.game(n: Int) = tichu.domain.GameKey(tournamentId, roundNumber, tableNumber, n)

    private fun confirm(teamId: TeamId, seenGames: List<SeenGame> = seen(4)) =
        confirmTable.confirm(tableKey, seenGames, principal(teamId))

    private fun retract(teamId: TeamId) = confirmTable.retract(tableKey, principal(teamId))

    // ---- confirm ----------------------------------------------------------------

    @Test
    fun `the first confirmation moves the table to confirming, the second completes it`() {
        enterAll(4)

        val first = confirm(teamA)
        assertEquals(TableStatus.CONFIRMING, first.status)
        assertEquals(listOf(teamA), first.confirmedTeamIds)

        val second = confirm(teamB)
        assertEquals(TableStatus.COMPLETED, second.status)
        assertEquals(listOf(teamA, teamB), second.confirmedTeamIds)
    }

    @Test
    fun `confirming before every game is entered is rejected as incomplete`() {
        enterAll(3)

        assertFailsWith<TableIncompleteException> { confirm(teamA, seen(3)) }

        assertEquals(emptySet(), confirmationRepository.findByTable(tableKey))
    }

    @Test
    fun `a stale version is rejected and records nothing`() {
        enterAll(4)
        val stale = seen(4)
        recordGame.update(tournamentId, 1, 1, request(2, teamA, teamB, version = 1), principal(teamB))

        assertFailsWith<GameConcurrentModificationException> { confirm(teamA, stale) }

        assertEquals(emptySet(), confirmationRepository.findByTable(tableKey))
    }

    @Test
    fun `seen games that miss a game number are a stale view`() {
        enterAll(4)

        assertFailsWith<GameConcurrentModificationException> { confirm(teamA, seen(4).dropLast(1)) }
        assertFailsWith<GameConcurrentModificationException> { confirm(teamA, seen(4) + SeenGame(5, 1)) }
    }

    @Test
    fun `duplicate game numbers in the seen games are malformed`() {
        enterAll(4)

        val error = assertFailsWith<InvalidFieldException> { confirm(teamA, seen(4) + SeenGame(1, 1)) }
        assertEquals("games", error.field)
    }

    @Test
    fun `confirming twice succeeds and changes nothing, even with stale versions`() {
        enterAll(4)
        confirm(teamA)

        val again = confirm(teamA, emptyList())

        assertEquals(TableStatus.CONFIRMING, again.status)
        assertEquals(listOf(teamA), again.confirmedTeamIds)
    }

    @Test
    fun `a team not seated at the table is forbidden to confirm and retract`() {
        enterAll(4)

        assertFailsWith<ForbiddenException> { confirm(teamC) }
        assertFailsWith<ForbiddenException> { retract(teamC) }
    }

    @Test
    fun `an unknown round or table is not found`() {
        assertFailsWith<NotFoundException> { confirmTable.confirm(TableKey(tournamentId, 2, 1), emptyList(), principal(teamA)) }
        assertFailsWith<NotFoundException> { confirmTable.retract(TableKey(tournamentId, 1, 9), principal(teamA)) }
    }

    @Test
    fun `a path naming another tournament is not found`() {
        assertFailsWith<NotFoundException> {
            confirmTable.confirm(TableKey(TournamentId("t-other"), 1, 1), emptyList(), principal(teamA))
        }
    }

    // ---- retract ----------------------------------------------------------------

    @Test
    fun `retracting the last confirmation returns the table to playing and games are editable again`() {
        enterAll(4)
        confirm(teamA)

        val state = retract(teamA)

        assertEquals(TableStatus.PLAYING, state.status)
        assertEquals(emptyList(), state.confirmedTeamIds)
        recordGame.update(tournamentId, 1, 1, request(1, teamA, teamB, version = 1), principal(teamB))
    }

    @Test
    fun `retracting when not confirmed succeeds and changes nothing`() {
        enterAll(4)
        confirm(teamB)

        val state = retract(teamA)

        assertEquals(TableStatus.CONFIRMING, state.status)
        assertEquals(listOf(teamB), state.confirmedTeamIds)
    }

    @Test
    fun `retracting on a completed table is rejected`() {
        enterAll(4)
        confirm(teamA)
        confirm(teamB)

        assertFailsWith<TableCompletedException> { retract(teamA) }

        assertEquals(setOf(teamA, teamB), confirmationRepository.findByTable(tableKey))
    }

    // ---- trio -------------------------------------------------------------------

    @Test
    fun `a trio table needs all six games and completes with the third confirmation`() {
        seat(teamA, teamB, teamC)
        enterAll(4)
        assertFailsWith<TableIncompleteException> { confirm(teamA, seen(4)) }
        enterGame(5)
        enterGame(6)

        assertEquals(TableStatus.CONFIRMING, confirm(teamA, seen(6)).status)
        assertEquals(TableStatus.CONFIRMING, confirm(teamC, seen(6)).status)
        val last = confirm(teamB, seen(6))

        assertEquals(TableStatus.COMPLETED, last.status)
        assertEquals(listOf(teamA, teamB, teamC), last.confirmedTeamIds, "seat order")
    }
}
