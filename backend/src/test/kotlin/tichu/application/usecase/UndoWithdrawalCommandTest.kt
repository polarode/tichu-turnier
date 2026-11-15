package tichu.application.usecase

import tichu.application.api.AuthenticatedTeam
import tichu.application.api.GameWriteRequest
import tichu.application.api.PlayerResultInput
import tichu.application.api.RecordGame
import tichu.application.api.TeamResultInput
import tichu.application.api.UndoWithdrawal
import tichu.application.api.WithdrawTeam
import tichu.domain.AdminUserId
import tichu.domain.CoOrganizer
import tichu.domain.ForfeitCompensation
import tichu.domain.Pairing
import tichu.domain.Player
import tichu.domain.PlayerId
import tichu.domain.Round
import tichu.domain.RoundId
import tichu.domain.Table
import tichu.domain.TableId
import tichu.domain.TableKey
import tichu.domain.Team
import tichu.domain.TeamId
import tichu.domain.TichuCall
import tichu.domain.Tournament
import tichu.domain.TournamentEvent
import tichu.domain.TournamentEventType
import tichu.domain.TournamentId
import tichu.domain.TournamentSettings
import tichu.domain.TournamentStatus
import tichu.domain.WithdrawalReason
import tichu.infra.persistence.InMemoryTournamentRepository
import tichu.testsupport.InMemoryAdminUserRepository
import tichu.testsupport.InMemoryCoOrganizerRepository
import tichu.testsupport.InMemoryGameRepository
import tichu.testsupport.InMemoryRoundRepository
import tichu.testsupport.InMemoryTableConfirmationRepository
import tichu.testsupport.InMemoryTeamRepository
import tichu.testsupport.RecordingTournamentEvents
import tichu.vocabulary.ErrorCode
import tichu.vocabulary.NotFoundException
import tichu.vocabulary.TeamNotWithdrawnException
import tichu.vocabulary.WithdrawalFinalException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Use-case tests for undoing a Withdrawal (ADR 0011), at the inbound-port seam with
 * in-memory doubles. Undo removes the Forfeit Games of the withdrawal's table, which
 * returns that table to Playing, and is refused once it is final: published, a later
 * round drawn, or a completed table it would leave unfinished.
 */
class UndoWithdrawalCommandTest {

    private val tournamentId = TournamentId("t-1")
    private val teamA = TeamId("team-a")
    private val teamB = TeamId("team-b")
    private val playerA1 = PlayerId("team-a-p1")
    private val playerA2 = PlayerId("team-a-p2")
    private val playerB1 = PlayerId("team-b-p1")
    private val playerB2 = PlayerId("team-b-p2")
    private val seatedPrincipal = AuthenticatedTeam(teamA, tournamentId)

    private val lead = AdminUserId("admin-lead")
    private val coOrganizer = AdminUserId("admin-co")
    private val stranger = AdminUserId("admin-stranger")
    private val now = Instant.parse("2026-11-12T18:45:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private val tableKey = TableKey(tournamentId, 1, 1)
    private val withdrawnEvent = TournamentEvent(TournamentEventType.TEAM_WITHDRAWAL_CHANGED, tournamentId)

    private val tournamentRepository = InMemoryTournamentRepository()
    private val coOrganizerRepository = InMemoryCoOrganizerRepository()
    private val teamRepository = InMemoryTeamRepository()
    private val roundRepository = InMemoryRoundRepository()
    private val gameRepository = InMemoryGameRepository()
    private val confirmationRepository = InMemoryTableConfirmationRepository()
    private val tournamentEvents = RecordingTournamentEvents()
    private val access = TournamentAccess(tournamentRepository, coOrganizerRepository, InMemoryAdminUserRepository())

    private val recordGame: RecordGame = RecordGameCommand(
        tournamentRepository,
        roundRepository,
        teamRepository,
        gameRepository,
        confirmationRepository,
        tournamentEvents,
    )

    private val withdrawTeam: WithdrawTeam = WithdrawTeamCommand(
        tournamentRepository,
        access,
        roundRepository,
        teamRepository,
        clock,
        tournamentEvents,
    )

    private val undoWithdrawal: UndoWithdrawal = UndoWithdrawalCommand(
        tournamentRepository,
        access,
        roundRepository,
        teamRepository,
        gameRepository,
        confirmationRepository,
        tournamentEvents,
    )

    @BeforeEach
    fun setUp() {
        tournamentRepository.save(
            Tournament(
                id = tournamentId,
                ownerId = lead,
                name = "Spring Classic",
                description = "",
                maxTeams = 16,
                settings = TournamentSettings(),
                status = TournamentStatus.ACTIVE,
                createdAt = Instant.parse("2026-01-01T12:00:00Z"),
                updatedAt = Instant.parse("2026-01-01T12:00:00Z"),
            ),
        )
        coOrganizerRepository.add(CoOrganizer(tournamentId, coOrganizer, Instant.parse("2026-01-01T12:00:00Z")))
        teamRepository.save(team(teamA, "Team Alpha", playerA1, playerA2))
        teamRepository.save(team(teamB, "Team Beta", playerB1, playerB2))
        roundRepository.save(
            Round(
                id = RoundId("r-1"),
                tournamentId = tournamentId,
                number = 1,
                pairing = Pairing(tables = listOf(Table(TableId(1), listOf(teamA, teamB)))),
            ),
        )
    }

    private fun team(id: TeamId, name: String, p1: PlayerId, p2: PlayerId) = Team(
        id = id,
        tournamentId = tournamentId,
        name = name,
        players = listOf(
            Player(p1, tournamentId, id, "P1 of $name"),
            Player(p2, tournamentId, id, "P2 of $name"),
        ),
    )

    private fun validRequest(gameNumber: Int) = GameWriteRequest(
        gameNumber = gameNumber,
        beschiss = false,
        version = null,
        teams = listOf(
            TeamResultInput(
                teamId = teamA,
                baseScore = 55,
                players = listOf(
                    PlayerResultInput(playerA1, 1, TichuCall.SMALL, 0),
                    PlayerResultInput(playerA2, 3, TichuCall.NONE, 0),
                ),
            ),
            TeamResultInput(
                teamId = teamB,
                baseScore = 45,
                players = listOf(
                    PlayerResultInput(playerB1, 2, TichuCall.NONE, 0),
                    PlayerResultInput(playerB2, 4, TichuCall.NONE, 0),
                ),
            ),
        ),
    )

    private fun enterGames(vararg gameNumbers: Int) {
        gameNumbers.forEach { recordGame.create(tournamentId, 1, 1, validRequest(it), seatedPrincipal) }
    }

    private fun confirm(teams: Set<TeamId>) {
        teams.forEach { confirmationRepository.add(tableKey, it, now) }
    }

    private fun setStatus(status: TournamentStatus) {
        tournamentRepository.save(tournamentRepository.findById(tournamentId)!!.copy(status = status))
    }

    private fun withdrawA() = withdrawTeam.withdraw(tournamentId, teamA, WithdrawalReason.DISQUALIFIED, ForfeitCompensation.FULL, lead)

    /**
     * Game 1 entered and B confirmed, then A withdraws mid-table: games 2 to 4 are
     * Forfeit Games for B, and the table is completed on B's confirmation alone.
     */
    private fun withdrawnMidTable() {
        enterGames(1)
        confirm(setOf(teamB))
        withdrawA()
    }

    private fun withdrawalOfA() = assertNotNull(teamRepository.findById(teamA, tournamentId)!!.withdrawal)

    // ---- forfeits at the table ----------------------------------------------------------

    @Test
    fun `undoing a withdrawal that produced forfeits removes the table's confirmations and clears the withdrawal`() {
        withdrawnMidTable()
        assertEquals(setOf(teamB), confirmationRepository.findByTable(tableKey))
        tournamentEvents.published.clear()

        undoWithdrawal.undo(tournamentId, teamA, lead)

        assertEquals(emptySet(), confirmationRepository.findByTable(tableKey), "table is back to Playing")
        assertNull(teamRepository.findById(teamA, tournamentId)!!.withdrawal)
        assertEquals(listOf(withdrawnEvent), tournamentEvents.published)
    }

    @Test
    fun `undoing a withdrawal that produced no forfeits keeps the table's confirmations`() {
        enterGames(1, 2, 3, 4)
        confirm(setOf(teamA, teamB))
        withdrawA()

        undoWithdrawal.undo(tournamentId, teamA, lead)

        assertEquals(setOf(teamA, teamB), confirmationRepository.findByTable(tableKey))
        assertNull(teamRepository.findById(teamA, tournamentId)!!.withdrawal)
    }

    // ---- final withdrawals ----------------------------------------------------------------

    @Test
    fun `undo is refused once a later round has been drawn, and the withdrawal stands`() {
        withdrawnMidTable()
        roundRepository.save(
            Round(
                id = RoundId("r-2"),
                tournamentId = tournamentId,
                number = 2,
                pairing = Pairing(tables = listOf(Table(TableId(1), listOf(teamA, teamB)))),
            ),
        )

        val ex = assertFailsWith<WithdrawalFinalException> { undoWithdrawal.undo(tournamentId, teamA, lead) }

        assertEquals(ErrorCode.WITHDRAWAL_FINAL, ex.code)
        withdrawalOfA()
        assertEquals(setOf(teamB), confirmationRepository.findByTable(tableKey))
    }

    @Test
    fun `undo is refused once the results are published`() {
        withdrawnMidTable()
        setStatus(TournamentStatus.PUBLISHED)

        val ex = assertFailsWith<WithdrawalFinalException> { undoWithdrawal.undo(tournamentId, teamA, lead) }

        assertEquals(ErrorCode.WITHDRAWAL_FINAL, ex.code)
        withdrawalOfA()
    }

    @Test
    fun `undo is refused in a completed tournament when the withdrawal produced forfeits`() {
        withdrawnMidTable()
        setStatus(TournamentStatus.COMPLETED)

        val ex = assertFailsWith<WithdrawalFinalException> { undoWithdrawal.undo(tournamentId, teamA, lead) }

        assertEquals(ErrorCode.WITHDRAWAL_FINAL, ex.code)
        withdrawalOfA()
        assertEquals(setOf(teamB), confirmationRepository.findByTable(tableKey))
    }

    @Test
    fun `undo is refused in a completed tournament when the team had not confirmed its table`() {
        enterGames(1, 2, 3, 4)
        confirm(setOf(teamB))
        withdrawA()
        setStatus(TournamentStatus.COMPLETED)

        val ex = assertFailsWith<WithdrawalFinalException> { undoWithdrawal.undo(tournamentId, teamA, lead) }

        assertEquals(ErrorCode.WITHDRAWAL_FINAL, ex.code)
        withdrawalOfA()
    }

    @Test
    fun `undo is allowed in a completed tournament when the table was completed with the team's own confirmation`() {
        enterGames(1, 2, 3, 4)
        confirm(setOf(teamA, teamB))
        withdrawA()
        setStatus(TournamentStatus.COMPLETED)

        undoWithdrawal.undo(tournamentId, teamA, lead)

        assertNull(teamRepository.findById(teamA, tournamentId)!!.withdrawal)
        assertEquals(setOf(teamA, teamB), confirmationRepository.findByTable(tableKey))
    }

    // ---- state and access -----------------------------------------------------------------

    @Test
    fun `undoing a team that is not withdrawn is refused`() {
        val ex = assertFailsWith<TeamNotWithdrawnException> { undoWithdrawal.undo(tournamentId, teamA, lead) }

        assertEquals(ErrorCode.TEAM_NOT_WITHDRAWN, ex.code)
        assertEquals(emptyList(), tournamentEvents.published)
    }

    @Test
    fun `a co-organizer may undo a withdrawal, while a stranger gets the same not-found as any organizer-only verb`() {
        withdrawnMidTable()

        val strangerEx = assertFailsWith<NotFoundException> { undoWithdrawal.undo(tournamentId, teamA, stranger) }
        assertEquals(ErrorCode.NOT_FOUND, strangerEx.code)
        withdrawalOfA()

        undoWithdrawal.undo(tournamentId, teamA, coOrganizer)
        assertNull(teamRepository.findById(teamA, tournamentId)!!.withdrawal)
    }
}
