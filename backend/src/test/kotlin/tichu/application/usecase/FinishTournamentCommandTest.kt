package tichu.application.usecase

import tichu.domain.AdminUserId
import tichu.domain.GameKey
import tichu.domain.Pairing
import tichu.domain.Round
import tichu.domain.RoundId
import tichu.domain.Table
import tichu.domain.TableId
import tichu.domain.TableKey
import tichu.domain.TeamId
import tichu.domain.Tournament
import tichu.domain.TournamentEvent
import tichu.domain.TournamentEventType
import tichu.domain.TournamentId
import tichu.domain.TournamentSettings
import tichu.domain.TournamentStatus
import tichu.infra.persistence.InMemoryTournamentRepository
import tichu.testsupport.InMemoryGameRepository
import tichu.testsupport.InMemoryRoundRepository
import tichu.testsupport.InMemoryTableConfirmationRepository
import tichu.testsupport.InMemoryTeamRepository
import tichu.testsupport.MutableClock
import tichu.testsupport.RecordingTournamentEvents
import tichu.testsupport.baseScoreGame
import tichu.testsupport.teamOf
import tichu.vocabulary.ErrorCode
import tichu.vocabulary.NotFoundException
import tichu.vocabulary.RoundIncompleteException
import tichu.vocabulary.RoundsRemainingException
import tichu.vocabulary.TournamentNotActiveException
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Use-case tests for finishing the tournament (S7c, #116): the tournament becomes
 * COMPLETED only once every Total Round is played and the last round is complete.
 */
class FinishTournamentCommandTest {

    private val tournamentId = TournamentId("t-1")
    private val owner = AdminUserId("admin-owner")
    private val now = Instant.parse("2026-01-01T12:00:00Z")

    private val tournamentRepository = InMemoryTournamentRepository()
    private val teamRepository = InMemoryTeamRepository()
    private val roundRepository = InMemoryRoundRepository()
    private val gameRepository = InMemoryGameRepository()
    private val confirmationRepository = InMemoryTableConfirmationRepository()
    private val clock = MutableClock(now)
    private val finishEvents = RecordingTournamentEvents()

    private val command = FinishTournamentCommand(
        tournamentRepository,
        PlayedRoundsReader(roundRepository, gameRepository, confirmationRepository),
        clock,
        finishEvents,
    )

    private fun tournament(status: TournamentStatus = TournamentStatus.ACTIVE, totalRounds: Int = 2) =
        tournamentRepository.save(
            Tournament(tournamentId, owner, "Spring Classic", "", 16, TournamentSettings(totalRounds = totalRounds), status, now, now),
        )

    private fun teams(vararg ids: String) = ids.forEach { teamRepository.save(teamOf(tournamentId, it)) }

    private fun round(number: Int, vararg tables: List<String>) = roundRepository.save(
        Round(
            RoundId("r-$number"),
            tournamentId,
            number,
            Pairing(tables.mapIndexed { i, seated -> Table(TableId(i + 1), seated.map(::TeamId)) }),
        ),
    )

    /**
     * Enters every scheduled game of [tableNumber] in [roundNumber], won by the
     * playing team seated first with [base] of the 100 base points, then confirms
     * it for every seated team when [confirmed].
     */
    private fun play(roundNumber: Int, tableNumber: Int, base: Int = 70, confirmed: Boolean = true) {
        val table = roundRepository.findByTournamentAndNumber(tournamentId, roundNumber)!!.table(tableNumber)!!
        table.schedule.forEach { scheduled ->
            val (first, second) = scheduled.playingTeams
            gameRepository.create(baseScoreGame(GameKey(tournamentId, roundNumber, tableNumber, scheduled.gameNumber), first, second, base))
        }
        if (confirmed) {
            table.seatedTeams.forEach { confirmationRepository.add(TableKey(tournamentId, roundNumber, tableNumber), it, now) }
        }
    }

    private fun statusOf(): TournamentStatus = tournamentRepository.findById(tournamentId)!!.status

    @Test
    fun `the tournament finishes once the last round is complete`() {
        tournament(totalRounds = 2)
        teams("a", "b", "c", "d")
        round(1, listOf("a", "b"), listOf("c", "d"))
        play(1, 1)
        play(1, 2)
        round(2, listOf("a", "c"), listOf("b", "d"))
        play(2, 1)
        play(2, 2)
        clock.advance(Duration.ofMinutes(5))

        val finished = command.finish(tournamentId, owner)

        assertEquals(TournamentStatus.COMPLETED, finished.status)
        assertEquals(now.plus(Duration.ofMinutes(5)), finished.updatedAt)
        assertEquals(TournamentStatus.COMPLETED, statusOf())
    }

    @Test
    fun `finishing is blocked while rounds remain`() {
        tournament(totalRounds = 2)
        teams("a", "b")
        round(1, listOf("a", "b"))
        play(1, 1)

        val ex = assertFailsWith<RoundsRemainingException> { command.finish(tournamentId, owner) }

        assertEquals(ErrorCode.ROUNDS_REMAINING, ex.code)
        assertEquals(TournamentStatus.ACTIVE, statusOf())
    }

    @Test
    fun `finishing before any round is played is blocked without a crash`() {
        tournament(totalRounds = 2)

        val ex = assertFailsWith<RoundsRemainingException> { command.finish(tournamentId, owner) }

        assertEquals(ErrorCode.ROUNDS_REMAINING, ex.code)
        assertEquals(TournamentStatus.ACTIVE, statusOf())
    }

    @Test
    fun `finishing is blocked while the last round has an unconfirmed table`() {
        tournament(totalRounds = 1)
        teams("a", "b", "c", "d")
        round(1, listOf("a", "b"), listOf("c", "d"))
        play(1, 1)
        play(1, 2, confirmed = false)

        val ex = assertFailsWith<RoundIncompleteException> { command.finish(tournamentId, owner) }

        assertEquals(ErrorCode.ROUND_INCOMPLETE, ex.code)
        assertEquals(TournamentStatus.ACTIVE, statusOf())
    }

    @Test
    fun `only an active tournament finishes`() {
        for (status in listOf(TournamentStatus.SETUP, TournamentStatus.COMPLETED)) {
            tournament(status, totalRounds = 1)

            val ex = assertFailsWith<TournamentNotActiveException> { command.finish(tournamentId, owner) }

            assertEquals(ErrorCode.TOURNAMENT_NOT_ACTIVE, ex.code)
            assertEquals(status, statusOf())
        }
    }

    @Test
    fun `a foreign admin cannot finish another admin's tournament`() {
        tournament(totalRounds = 1)
        teams("a", "b")
        round(1, listOf("a", "b"))
        play(1, 1)

        assertFailsWith<NotFoundException> { command.finish(tournamentId, AdminUserId("admin-intruder")) }
        assertEquals(TournamentStatus.ACTIVE, statusOf())
    }

    // ---- live events ------------------------------------------------------------

    @Test
    fun `finishing the tournament publishes exactly one tournament-finished event`() {
        tournament(totalRounds = 1)
        teams("a", "b")
        round(1, listOf("a", "b"))
        play(1, 1)

        command.finish(tournamentId, owner)

        assertEquals(listOf(TournamentEvent(TournamentEventType.TOURNAMENT_FINISHED, tournamentId)), finishEvents.published)
    }

    @Test
    fun `finishing blocked while rounds remain publishes nothing`() {
        tournament(totalRounds = 2)
        teams("a", "b")
        round(1, listOf("a", "b"))
        play(1, 1)

        assertFailsWith<RoundsRemainingException> { command.finish(tournamentId, owner) }

        assertEquals(emptyList(), finishEvents.published)
    }

    @Test
    fun `finishing blocked while the last round is incomplete publishes nothing`() {
        tournament(totalRounds = 1)
        teams("a", "b", "c", "d")
        round(1, listOf("a", "b"), listOf("c", "d"))
        play(1, 1)
        play(1, 2, confirmed = false)

        assertFailsWith<RoundIncompleteException> { command.finish(tournamentId, owner) }

        assertEquals(emptyList(), finishEvents.published)
    }

    @Test
    fun `finishing an inactive tournament publishes nothing`() {
        for (status in listOf(TournamentStatus.SETUP, TournamentStatus.COMPLETED)) {
            tournament(status, totalRounds = 1)

            assertFailsWith<TournamentNotActiveException> { command.finish(tournamentId, owner) }
        }

        assertEquals(emptyList(), finishEvents.published)
    }

    @Test
    fun `a foreign admin's finish publishes nothing`() {
        tournament(totalRounds = 1)
        teams("a", "b")
        round(1, listOf("a", "b"))
        play(1, 1)

        assertFailsWith<NotFoundException> { command.finish(tournamentId, AdminUserId("admin-intruder")) }

        assertEquals(emptyList(), finishEvents.published)
    }
}
