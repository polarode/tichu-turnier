package tichu.application.usecase

import tichu.domain.AdminUserId
import tichu.domain.Pairing
import tichu.domain.Round
import tichu.domain.RoundId
import tichu.domain.Table
import tichu.domain.TableId
import tichu.domain.TeamId
import tichu.domain.Tournament
import tichu.domain.TournamentEvent
import tichu.domain.TournamentEventType
import tichu.domain.TournamentId
import tichu.domain.TournamentSettings
import tichu.domain.TournamentStatus
import tichu.infra.persistence.InMemoryTournamentRepository
import tichu.testsupport.InMemoryRoundRepository
import tichu.testsupport.RecordingTournamentEvents
import tichu.vocabulary.ErrorCode
import tichu.vocabulary.InvalidFieldException
import tichu.vocabulary.NotFoundException
import tichu.vocabulary.TotalRoundsBelowRoundCountException
import tichu.vocabulary.TournamentNotEditableException
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Use-case tests for changing the Total Rounds (GLOSSARY.md) while ACTIVE — the
 * one setting still adjustable once the tournament has started, never below the
 * rounds already drawn and never after the finish.
 */
class SetTotalRoundsCommandTest {

    private val tournamentId = TournamentId("t-1")
    private val owner = AdminUserId("admin-owner")
    private val created = Instant.parse("2026-01-01T12:00:00Z")
    private val clock = Clock.fixed(Instant.parse("2026-01-02T08:00:00Z"), ZoneOffset.UTC)

    private val tournamentRepository = InMemoryTournamentRepository()
    private val roundRepository = InMemoryRoundRepository()
    private val totalRoundsEvents = RecordingTournamentEvents()
    private val command = SetTotalRoundsCommand(tournamentRepository, roundRepository, clock, totalRoundsEvents)

    private fun tournament(status: TournamentStatus, settings: TournamentSettings = TournamentSettings(grandTichuBonus = 100)) =
        tournamentRepository.save(
            Tournament(tournamentId, owner, "Spring Classic", "", 16, settings, status, created, created),
        )

    private fun createRounds(count: Int) = (1..count).forEach { number ->
        roundRepository.save(
            Round(
                RoundId("r-$number"),
                tournamentId,
                number,
                Pairing(listOf(Table(TableId(1), listOf(TeamId("a"), TeamId("b"))))),
            ),
        )
    }

    @Test
    fun `an active tournament's total rounds change, other settings untouched, updatedAt bumped`() {
        tournament(TournamentStatus.ACTIVE)
        createRounds(2)

        val updated = command.set(tournamentId, 6, owner)

        assertEquals(TournamentSettings(grandTichuBonus = 100, totalRounds = 6), updated.settings)
        assertEquals(clock.instant(), updated.updatedAt)
        assertEquals(6, tournamentRepository.findById(tournamentId)!!.settings.totalRounds)
    }

    @Test
    fun `total rounds may drop to the created round count but not below it, anchored to the field`() {
        tournament(TournamentStatus.ACTIVE)
        createRounds(3)

        val ex = assertFailsWith<TotalRoundsBelowRoundCountException> { command.set(tournamentId, 2, owner) }
        assertEquals(ErrorCode.TOTAL_ROUNDS_BELOW_ROUND_COUNT, ex.code)
        assertEquals("totalRounds", ex.field)

        assertEquals(3, command.set(tournamentId, 3, owner).settings.totalRounds)
    }

    @Test
    fun `total rounds outside 1 to 20 are rejected anchored to the field`() {
        tournament(TournamentStatus.ACTIVE)

        for (totalRounds in listOf(0, 21)) {
            val ex = assertFailsWith<InvalidFieldException> { command.set(tournamentId, totalRounds, owner) }
            assertEquals("totalRounds", ex.field)
        }
    }

    @Test
    fun `in setup the total rounds change through the tournament form, not here`() {
        tournament(TournamentStatus.SETUP)

        assertFailsWith<TournamentNotEditableException> { command.set(tournamentId, 5, owner) }
        assertEquals(4, tournamentRepository.findById(tournamentId)!!.settings.totalRounds)
    }

    @Test
    fun `a completed tournament's total rounds are locked`() {
        tournament(TournamentStatus.COMPLETED)

        val ex = assertFailsWith<TournamentNotEditableException> { command.set(tournamentId, 5, owner) }
        assertEquals(ErrorCode.TOURNAMENT_NOT_EDITABLE, ex.code)
    }

    @Test
    fun `a foreign admin cannot change another admin's total rounds`() {
        tournament(TournamentStatus.ACTIVE)

        assertFailsWith<NotFoundException> { command.set(tournamentId, 5, AdminUserId("admin-intruder")) }
        assertEquals(4, tournamentRepository.findById(tournamentId)!!.settings.totalRounds)
    }

    // ---- live events ------------------------------------------------------------

    @Test
    fun `changing the total rounds publishes one total-rounds-changed event`() {
        tournament(TournamentStatus.ACTIVE)
        createRounds(2)

        command.set(tournamentId, 6, owner)

        assertEquals(
            listOf(TournamentEvent(TournamentEventType.TOTAL_ROUNDS_CHANGED, tournamentId)),
            totalRoundsEvents.published,
        )
    }

    @Test
    fun `rejected total rounds changes publish nothing`() {
        tournament(TournamentStatus.ACTIVE)
        createRounds(3)

        assertFailsWith<TotalRoundsBelowRoundCountException> { command.set(tournamentId, 2, owner) }
        assertFailsWith<InvalidFieldException> { command.set(tournamentId, 21, owner) }
        assertFailsWith<NotFoundException> { command.set(tournamentId, 5, AdminUserId("admin-intruder")) }

        assertEquals(emptyList(), totalRoundsEvents.published)
    }

    @Test
    fun `total rounds changes outside the active status publish nothing`() {
        for (status in listOf(TournamentStatus.SETUP, TournamentStatus.COMPLETED)) {
            tournament(status)

            assertFailsWith<TournamentNotEditableException> { command.set(tournamentId, 5, owner) }
        }

        assertEquals(emptyList(), totalRoundsEvents.published)
    }

    @Test
    fun `keeping the same total rounds publishes nothing and leaves the tournament unchanged`() {
        tournament(TournamentStatus.ACTIVE)
        createRounds(2)

        val unchanged = command.set(tournamentId, 4, owner)

        assertEquals(TournamentSettings(grandTichuBonus = 100, totalRounds = 4), unchanged.settings)
        assertEquals(created, unchanged.updatedAt)
        assertEquals(created, tournamentRepository.findById(tournamentId)!!.updatedAt)
        assertEquals(emptyList(), totalRoundsEvents.published)
    }
}
