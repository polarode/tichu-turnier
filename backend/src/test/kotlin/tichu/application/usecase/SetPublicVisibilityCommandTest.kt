package tichu.application.usecase

import tichu.domain.AdminUserId
import tichu.domain.Tournament
import tichu.domain.TournamentEvent
import tichu.domain.TournamentEventType
import tichu.domain.TournamentId
import tichu.domain.TournamentSettings
import tichu.domain.TournamentStatus
import tichu.infra.persistence.InMemoryTournamentRepository
import tichu.testsupport.RecordingTournamentEvents
import tichu.vocabulary.NotFoundException
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Use-case tests for the public visibility switch. Unlike the Total Rounds, it has
 * no status gate: the organizer can flip it in every Tournament Status.
 */
class SetPublicVisibilityCommandTest {

    private val tournamentId = TournamentId("t-1")
    private val owner = AdminUserId("admin-owner")
    private val created = Instant.parse("2026-01-01T12:00:00Z")
    private val clock = Clock.fixed(Instant.parse("2026-01-02T08:00:00Z"), ZoneOffset.UTC)

    private val tournamentRepository = InMemoryTournamentRepository()
    private val visibilityEvents = RecordingTournamentEvents()
    private val command = SetPublicVisibilityCommand(tournamentRepository, TournamentAccess(tournamentRepository), clock, visibilityEvents)

    private fun tournament(
        status: TournamentStatus,
        publicVisibility: Boolean = false,
    ) = tournamentRepository.save(
        Tournament(
            tournamentId, owner, "Spring Classic", "", 16,
            TournamentSettings(grandTichuBonus = 100), status, created, created,
            publicVisibility = publicVisibility,
        ),
    )

    @Test
    fun `switching public visibility on and back off persists each value`() {
        tournament(TournamentStatus.SETUP)

        assertTrue(command.set(tournamentId, true, owner).publicVisibility)
        assertTrue(tournamentRepository.findById(tournamentId)!!.publicVisibility)

        assertFalse(command.set(tournamentId, false, owner).publicVisibility)
        assertFalse(tournamentRepository.findById(tournamentId)!!.publicVisibility)
    }

    @Test
    fun `public visibility can be switched in every status`() {
        for (status in TournamentStatus.entries) {
            tournament(status)

            assertTrue(command.set(tournamentId, true, owner).publicVisibility, "status $status")
            assertTrue(tournamentRepository.findById(tournamentId)!!.publicVisibility, "status $status")
        }
    }

    @Test
    fun `a foreign admin cannot change another admin's public visibility`() {
        tournament(TournamentStatus.ACTIVE)

        assertFailsWith<NotFoundException> { command.set(tournamentId, true, AdminUserId("admin-intruder")) }
        assertFalse(tournamentRepository.findById(tournamentId)!!.publicVisibility)
    }

    @Test
    fun `an unknown tournament is not found`() {
        assertFailsWith<NotFoundException> { command.set(TournamentId("missing"), true, owner) }
    }

    @Test
    fun `changing public visibility publishes one public-visibility-changed event with the tournament id`() {
        tournament(TournamentStatus.PUBLISHED)

        command.set(tournamentId, true, owner)

        assertEquals(
            listOf(TournamentEvent(TournamentEventType.PUBLIC_VISIBILITY_CHANGED, tournamentId)),
            visibilityEvents.published,
        )
    }

    @Test
    fun `setting the value it already has publishes nothing and leaves the tournament unchanged`() {
        tournament(TournamentStatus.ACTIVE, publicVisibility = true)

        val unchanged = command.set(tournamentId, true, owner)

        assertTrue(unchanged.publicVisibility)
        assertEquals(created, unchanged.updatedAt)
        assertEquals(created, tournamentRepository.findById(tournamentId)!!.updatedAt)
        assertEquals(emptyList(), visibilityEvents.published)
    }

    @Test
    fun `rejected changes publish nothing`() {
        tournament(TournamentStatus.ACTIVE)

        assertFailsWith<NotFoundException> { command.set(tournamentId, true, AdminUserId("admin-intruder")) }

        assertEquals(emptyList(), visibilityEvents.published)
    }
}
