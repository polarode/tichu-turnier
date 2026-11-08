package tichu.application.usecase

import tichu.domain.AdminUserId
import tichu.domain.Tournament
import tichu.domain.TournamentEvent
import tichu.domain.TournamentEventType
import tichu.domain.TournamentId
import tichu.domain.TournamentSettings
import tichu.domain.TournamentStatus
import tichu.infra.persistence.InMemoryTournamentRepository
import tichu.testsupport.MutableClock
import tichu.testsupport.RecordingTournamentEvents
import tichu.vocabulary.ErrorCode
import tichu.vocabulary.NotFoundException
import tichu.vocabulary.TournamentNotCompletedException
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Use-case tests for publishing the results (S7c, #116): only a completed tournament
 * can be published, and publishing is one-way.
 */
class PublishTournamentCommandTest {

    private val tournamentId = TournamentId("t-1")
    private val owner = AdminUserId("admin-owner")
    private val now = Instant.parse("2026-01-01T12:00:00Z")

    private val tournamentRepository = InMemoryTournamentRepository()
    private val clock = MutableClock(now)

    private val publishEvents = RecordingTournamentEvents()
    private val command = PublishTournamentCommand(tournamentRepository, TournamentAccess(tournamentRepository), clock, publishEvents)

    private fun tournament(status: TournamentStatus) =
        tournamentRepository.save(
            Tournament(tournamentId, owner, "Spring Classic", "", 16, TournamentSettings(totalRounds = 2), status, now, now),
        )

    private fun statusOf(): TournamentStatus = tournamentRepository.findById(tournamentId)!!.status

    @Test
    fun `a completed tournament publishes`() {
        tournament(TournamentStatus.COMPLETED)
        clock.advance(Duration.ofMinutes(5))

        val published = command.publish(tournamentId, owner)

        assertEquals(TournamentStatus.PUBLISHED, published.status)
        assertEquals(now.plus(Duration.ofMinutes(5)), published.updatedAt)
        assertEquals(TournamentStatus.PUBLISHED, statusOf())
        assertEquals(now.plus(Duration.ofMinutes(5)), tournamentRepository.findById(tournamentId)!!.updatedAt)
    }

    @Test
    fun `only a completed tournament publishes`() {
        for (status in listOf(TournamentStatus.SETUP, TournamentStatus.ACTIVE, TournamentStatus.PUBLISHED)) {
            tournament(status)

            val ex = assertFailsWith<TournamentNotCompletedException> { command.publish(tournamentId, owner) }

            assertEquals(ErrorCode.TOURNAMENT_NOT_COMPLETED, ex.code)
            assertEquals(status, statusOf())
        }
    }

    @Test
    fun `a foreign admin cannot publish another admin's tournament`() {
        tournament(TournamentStatus.COMPLETED)

        assertFailsWith<NotFoundException> { command.publish(tournamentId, AdminUserId("admin-intruder")) }
        assertEquals(TournamentStatus.COMPLETED, statusOf())
    }

    // ---- live events ------------------------------------------------------------

    @Test
    fun `publishing the results publishes exactly one results-published event`() {
        tournament(TournamentStatus.COMPLETED)

        command.publish(tournamentId, owner)

        assertEquals(listOf(TournamentEvent(TournamentEventType.RESULTS_PUBLISHED, tournamentId)), publishEvents.published)
    }

    @Test
    fun `a publish of a tournament that is not completed publishes nothing`() {
        for (status in listOf(TournamentStatus.SETUP, TournamentStatus.ACTIVE, TournamentStatus.PUBLISHED)) {
            tournament(status)

            assertFailsWith<TournamentNotCompletedException> { command.publish(tournamentId, owner) }
        }

        assertEquals(emptyList(), publishEvents.published)
    }

    @Test
    fun `a foreign admin's publish publishes nothing`() {
        tournament(TournamentStatus.COMPLETED)

        assertFailsWith<NotFoundException> { command.publish(tournamentId, AdminUserId("admin-intruder")) }

        assertEquals(emptyList(), publishEvents.published)
    }
}
