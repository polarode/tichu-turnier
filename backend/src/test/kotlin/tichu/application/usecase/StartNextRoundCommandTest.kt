package tichu.application.usecase

import tichu.domain.AdminUserId
import tichu.domain.GameKey
import tichu.domain.IdGenerator
import tichu.domain.Pairing
import tichu.domain.Round
import tichu.domain.RoundId
import tichu.domain.Table
import tichu.domain.TableId
import tichu.domain.TableKey
import tichu.domain.TeamId
import tichu.domain.Tournament
import tichu.domain.TournamentId
import tichu.domain.TournamentSettings
import tichu.domain.TournamentStatus
import tichu.infra.persistence.InMemoryTournamentRepository
import tichu.testsupport.InMemoryGameRepository
import tichu.testsupport.InMemoryRoundRepository
import tichu.testsupport.InMemoryTableConfirmationRepository
import tichu.testsupport.InMemoryTeamRepository
import tichu.testsupport.baseScoreGame
import tichu.testsupport.teamOf
import tichu.vocabulary.ErrorCode
import tichu.vocabulary.NotFoundException
import tichu.vocabulary.RoundIncompleteException
import tichu.vocabulary.TotalRoundsReachedException
import tichu.vocabulary.TournamentNotActiveException
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Use-case tests for starting the next round (S7b, #115): round n+1 is drawn
 * from the current Standings by Swiss pairing, only once every table of the
 * current round is completed and while rounds remain.
 */
class StartNextRoundCommandTest {

    private val tournamentId = TournamentId("t-1")
    private val owner = AdminUserId("admin-owner")
    private val now = Instant.parse("2026-01-01T12:00:00Z")

    private val tournamentRepository = InMemoryTournamentRepository()
    private val teamRepository = InMemoryTeamRepository()
    private val roundRepository = InMemoryRoundRepository()
    private val gameRepository = InMemoryGameRepository()
    private val confirmationRepository = InMemoryTableConfirmationRepository()
    private val idGenerator = object : IdGenerator {
        private var counter = 0
        override fun newId(): String = "id-${++counter}"
    }

    private val command = StartNextRoundCommand(
        tournamentRepository,
        teamRepository,
        roundRepository,
        PlayedRoundsReader(roundRepository, gameRepository, confirmationRepository),
        idGenerator,
        Random(7),
    )

    private fun tournament(status: TournamentStatus = TournamentStatus.ACTIVE, totalRounds: Int = 4) =
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

    @Test
    fun `an even round is drawn from the standings in standings order`() {
        tournament()
        teams("a", "b", "c", "d")
        round(1, listOf("a", "b"), listOf("c", "d"))
        play(1, 1, base = 70)
        play(1, 2, base = 60)

        val next = command.start(tournamentId, owner)

        // Standings: a (8 VP, 280 TP), c (8, 240), d (0, 160), b (0, 120). Adjacent
        // pairing would be a-c, d-b — no rematch, so it stands.
        assertEquals(2, next.number)
        assertEquals(
            listOf(listOf(TeamId("a"), TeamId("c")), listOf(TeamId("d"), TeamId("b"))),
            next.pairing.tables.map { it.seatedTeams },
        )
        assertEquals(next, roundRepository.findByTournamentAndNumber(tournamentId, 2))
    }

    @Test
    fun `pairs around rematches from every previous round`() {
        tournament()
        teams("a", "b", "c", "d")
        round(1, listOf("a", "b"), listOf("c", "d"))
        play(1, 1, base = 70)
        play(1, 2, base = 60)
        round(2, listOf("a", "c"), listOf("b", "d"))
        play(2, 1, base = 60)
        play(2, 2, base = 70)

        val next = command.start(tournamentId, owner)

        // Standings a (16 VP, 520 TP), then b and c tied (8, 400; b first by name), d (0, 280).
        // Adjacent a-b and c-d are round-1 rematches and a-c a round-2 one: a-d, b-c is the only clean pairing.
        assertEquals(
            listOf(listOf(TeamId("a"), TeamId("d")), listOf(TeamId("b"), TeamId("c"))),
            next.pairing.tables.map { it.seatedTeams },
        )
    }

    @Test
    fun `an odd round seats the bottom three of the standings at the trio table`() {
        tournament()
        teams("a", "b", "c", "d", "e")
        round(1, listOf("a", "b"), listOf("c", "d", "e"))
        play(1, 1, base = 70)
        play(1, 2, base = 60)

        val next = command.start(tournamentId, owner)

        // Trio games are won by the first-seated playing team (schedule 1 CD, 2 CE, 3 DE, ...):
        // c 8 VP (240 TP), d 4 VP (200), e 0 VP (160); table 1: a 8 VP (280), b 0 VP (120).
        // Standings a, c, d, e, b → the bottom three d, e, b sit at the trio, last.
        assertEquals(
            listOf(listOf(TeamId("a"), TeamId("c")), listOf(TeamId("d"), TeamId("e"), TeamId("b"))),
            next.pairing.tables.map { it.seatedTeams },
        )
        assertEquals(true, next.pairing.tables.last().isTrio)
    }

    @Test
    fun `advancing waits until every table of the current round is completed`() {
        tournament()
        teams("a", "b", "c", "d")
        round(1, listOf("a", "b"), listOf("c", "d"))
        play(1, 1)
        play(1, 2, confirmed = false)

        val ex = assertFailsWith<RoundIncompleteException> { command.start(tournamentId, owner) }

        assertEquals(ErrorCode.ROUND_INCOMPLETE, ex.code)
        assertEquals(null, roundRepository.findByTournamentAndNumber(tournamentId, 2))
    }

    @Test
    fun `the last round cannot be followed by another`() {
        tournament(totalRounds = 1)
        teams("a", "b")
        round(1, listOf("a", "b"))
        play(1, 1)

        val ex = assertFailsWith<TotalRoundsReachedException> { command.start(tournamentId, owner) }

        assertEquals(ErrorCode.TOTAL_ROUNDS_REACHED, ex.code)
    }

    @Test
    fun `only an active tournament advances`() {
        for (status in listOf(TournamentStatus.SETUP, TournamentStatus.COMPLETED)) {
            tournament(status)

            val ex = assertFailsWith<TournamentNotActiveException> { command.start(tournamentId, owner) }
            assertEquals(ErrorCode.TOURNAMENT_NOT_ACTIVE, ex.code)
        }
    }

    @Test
    fun `a foreign admin cannot advance another admin's tournament`() {
        tournament()
        teams("a", "b")
        round(1, listOf("a", "b"))
        play(1, 1)

        assertFailsWith<NotFoundException> { command.start(tournamentId, AdminUserId("admin-intruder")) }
        assertEquals(null, roundRepository.findByTournamentAndNumber(tournamentId, 2))
    }
}
