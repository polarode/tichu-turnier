package tichu.application.usecase

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
import tichu.domain.Team
import tichu.domain.TeamId
import tichu.domain.Tournament
import tichu.domain.TournamentEvent
import tichu.domain.TournamentEventType
import tichu.domain.TournamentId
import tichu.domain.TournamentSettings
import tichu.domain.TournamentStatus
import tichu.domain.Withdrawal
import tichu.domain.WithdrawalReason
import tichu.infra.persistence.InMemoryTournamentRepository
import tichu.testsupport.InMemoryAdminUserRepository
import tichu.testsupport.InMemoryCoOrganizerRepository
import tichu.testsupport.InMemoryRoundRepository
import tichu.testsupport.InMemoryTeamRepository
import tichu.testsupport.RecordingTournamentEvents
import tichu.vocabulary.ErrorCode
import tichu.vocabulary.NotFoundException
import tichu.vocabulary.TeamAlreadyWithdrawnException
import tichu.vocabulary.TournamentNotStartedException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Use-case tests for the organizer's Withdrawal of a team (ADR 0011), at the inbound-port
 * seam with in-memory doubles. A withdrawal records its reason, compensation, the highest
 * drawn round, the instant and the organizer, and publishes a withdrawal-changed event.
 */
class WithdrawTeamCommandTest {

    private val tournamentId = TournamentId("t-1")
    private val teamA = TeamId("team-a")
    private val teamB = TeamId("team-b")
    private val playerA1 = PlayerId("team-a-p1")
    private val playerA2 = PlayerId("team-a-p2")
    private val playerB1 = PlayerId("team-b-p1")
    private val playerB2 = PlayerId("team-b-p2")

    private val lead = AdminUserId("admin-lead")
    private val coOrganizer = AdminUserId("admin-co")
    private val stranger = AdminUserId("admin-stranger")
    private val withdrawnAt = Instant.parse("2026-11-12T18:45:00Z")
    private val clock = Clock.fixed(withdrawnAt, ZoneOffset.UTC)

    private val tournamentRepository = InMemoryTournamentRepository()
    private val coOrganizerRepository = InMemoryCoOrganizerRepository()
    private val teamRepository = InMemoryTeamRepository()
    private val roundRepository = InMemoryRoundRepository()
    private val tournamentEvents = RecordingTournamentEvents()

    private val withdrawTeam: WithdrawTeam = WithdrawTeamCommand(
        tournamentRepository,
        TournamentAccess(tournamentRepository, coOrganizerRepository, InMemoryAdminUserRepository()),
        roundRepository,
        teamRepository,
        clock,
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
        drawRound(1)
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

    private fun drawRound(number: Int) {
        roundRepository.save(
            Round(
                id = RoundId("r-$number"),
                tournamentId = tournamentId,
                number = number,
                pairing = Pairing(tables = listOf(Table(TableId(1), listOf(teamA, teamB)))),
            ),
        )
    }

    private fun setStatus(status: TournamentStatus) {
        tournamentRepository.save(tournamentRepository.findById(tournamentId)!!.copy(status = status))
    }

    private val withdrawnEvent = TournamentEvent(TournamentEventType.TEAM_WITHDRAWAL_CHANGED, tournamentId)

    // ---- recording ------------------------------------------------------------------

    @Test
    fun `withdrawing a team in an active tournament stores its reason, compensation, round, instant and organizer, and publishes`() {
        val withdrawal = withdrawTeam.withdraw(tournamentId, teamA, WithdrawalReason.DISQUALIFIED, ForfeitCompensation.HALF, lead)

        val expected = Withdrawal(WithdrawalReason.DISQUALIFIED, ForfeitCompensation.HALF, 1, withdrawnAt, lead)
        assertEquals(expected, withdrawal)
        assertEquals(expected, teamRepository.findById(teamA, tournamentId)!!.withdrawal)
        assertEquals(listOf(withdrawnEvent), tournamentEvents.published)
    }

    @Test
    fun `the withdrawal is stamped with the highest drawn round`() {
        drawRound(2)

        val withdrawal = withdrawTeam.withdraw(tournamentId, teamA, WithdrawalReason.VOLUNTARY, ForfeitCompensation.NONE, lead)

        assertEquals(2, withdrawal.roundNumber)
    }

    // ---- tournament status ----------------------------------------------------------

    @Test
    fun `withdrawal is refused in setup and allowed once the tournament is completed or published`() {
        setStatus(TournamentStatus.SETUP)
        val ex = assertFailsWith<TournamentNotStartedException> {
            withdrawTeam.withdraw(tournamentId, teamA, WithdrawalReason.VOLUNTARY, ForfeitCompensation.NONE, lead)
        }
        assertEquals(ErrorCode.TOURNAMENT_NOT_STARTED, ex.code)
        assertNull(teamRepository.findById(teamA, tournamentId)!!.withdrawal)

        setStatus(TournamentStatus.COMPLETED)
        withdrawTeam.withdraw(tournamentId, teamA, WithdrawalReason.VOLUNTARY, ForfeitCompensation.NONE, lead)
        setStatus(TournamentStatus.PUBLISHED)
        withdrawTeam.withdraw(tournamentId, teamB, WithdrawalReason.DISQUALIFIED, ForfeitCompensation.FULL, lead)

        assertEquals(WithdrawalReason.DISQUALIFIED, teamRepository.findById(teamB, tournamentId)!!.withdrawal!!.reason)
    }

    // ---- team ----------------------------------------------------------------------

    @Test
    fun `a team that already withdrew cannot withdraw again, and the first withdrawal stands`() {
        val first = withdrawTeam.withdraw(tournamentId, teamA, WithdrawalReason.DISQUALIFIED, ForfeitCompensation.FULL, lead)
        tournamentEvents.published.clear()

        val ex = assertFailsWith<TeamAlreadyWithdrawnException> {
            withdrawTeam.withdraw(tournamentId, teamA, WithdrawalReason.VOLUNTARY, ForfeitCompensation.NONE, lead)
        }

        assertEquals(ErrorCode.TEAM_ALREADY_WITHDRAWN, ex.code)
        assertEquals(first, teamRepository.findById(teamA, tournamentId)!!.withdrawal)
        assertEquals(emptyList(), tournamentEvents.published)
    }

    @Test
    fun `a team that is not part of the tournament is not found and nothing is recorded`() {
        val foreign = TeamId("team-foreign")
        teamRepository.save(
            Team(
                id = foreign,
                tournamentId = TournamentId("t-2"),
                name = "Foreign",
                players = listOf(
                    Player(PlayerId("f-1"), TournamentId("t-2"), foreign, "F1"),
                    Player(PlayerId("f-2"), TournamentId("t-2"), foreign, "F2"),
                ),
            ),
        )

        val ex = assertFailsWith<NotFoundException> {
            withdrawTeam.withdraw(tournamentId, foreign, WithdrawalReason.DISQUALIFIED, ForfeitCompensation.FULL, lead)
        }

        assertEquals(ErrorCode.NOT_FOUND, ex.code)
        assertNull(teamRepository.findById(foreign)!!.withdrawal)
        assertEquals(emptyList(), tournamentEvents.published)
    }

    // ---- access ---------------------------------------------------------------------

    @Test
    fun `a co-organizer may withdraw a team, while a stranger gets the same not-found as any organizer-only verb`() {
        withdrawTeam.withdraw(tournamentId, teamA, WithdrawalReason.VOLUNTARY, ForfeitCompensation.HALF, coOrganizer)

        val ex = assertFailsWith<NotFoundException> {
            withdrawTeam.withdraw(tournamentId, teamB, WithdrawalReason.DISQUALIFIED, ForfeitCompensation.FULL, stranger)
        }

        assertEquals(ErrorCode.NOT_FOUND, ex.code)
        assertNull(teamRepository.findById(teamB, tournamentId)!!.withdrawal, "stranger withdrew nothing")
    }
}
