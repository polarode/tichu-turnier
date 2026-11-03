package tichu.infra.rest

import tichu.application.api.StartNextRound
import tichu.domain.AdminUserId
import tichu.domain.AdminUserRepository
import tichu.domain.GameKey
import tichu.domain.GameRepository
import tichu.domain.Pairing
import tichu.domain.PasswordHasher
import tichu.domain.Round
import tichu.domain.RoundId
import tichu.domain.RoundRepository
import tichu.domain.Table
import tichu.domain.TableConfirmationRepository
import tichu.domain.TableId
import tichu.domain.TableKey
import tichu.domain.TeamId
import tichu.domain.TeamRepository
import tichu.domain.Tournament
import tichu.domain.TournamentId
import tichu.domain.TournamentRepository
import tichu.domain.TournamentSettings
import tichu.domain.TournamentStatus
import tichu.main.Application
import tichu.testsupport.PostgresBackedIntegrationTest
import tichu.testsupport.baseScoreGame
import tichu.testsupport.loginAndCaptureSession
import tichu.testsupport.loginAsSeededAdmin
import tichu.testsupport.seedAdmin
import tichu.testsupport.teamOf
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.context.WebApplicationContext
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Integration test for starting the next round (S7b, #115) over MockMvc and the
 * real security chain against Testcontainers PostgreSQL: the wire shape, the
 * rejection codes, owner scoping and the tournament row lock that serializes
 * concurrent advances.
 */
@SpringBootTest(classes = [Application::class])
class NextRoundIntegrationTest(
    @Autowired private val context: WebApplicationContext,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val adminUsers: AdminUserRepository,
    @Autowired private val passwordHasher: PasswordHasher,
    @Autowired private val tournamentRepository: TournamentRepository,
    @Autowired private val teamRepository: TeamRepository,
    @Autowired private val roundRepository: RoundRepository,
    @Autowired private val gameRepository: GameRepository,
    @Autowired private val confirmationRepository: TableConfirmationRepository,
    @Autowired private val startNextRound: StartNextRound,
    @Autowired private val jdbcTemplate: JdbcTemplate,
    @Autowired private val transactionTemplate: TransactionTemplate,
) : PostgresBackedIntegrationTest() {

    private val mockMvc: MockMvc = MockMvcBuilders.webAppContextSetup(context)
        .apply<org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder>(springSecurity())
        .build()

    private val tournamentId = TournamentId("t-next")
    private val now = Instant.parse("2026-01-01T12:00:00Z")
    private var ownerId = AdminUserId("")
    private lateinit var ownerCookie: Cookie

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("DELETE FROM tournaments")
        val email = "owner-${UUID.randomUUID()}@example.com"
        ownerId = seedAdmin(adminUsers, passwordHasher, email).id
        ownerCookie = mockMvc.loginAndCaptureSession(objectMapper, email)
        tournament()
        listOf("a", "b", "c", "d").forEach { teamRepository.save(teamOf(tournamentId, it)) }
        roundRepository.save(
            Round(
                RoundId("r-1"),
                tournamentId,
                1,
                Pairing(listOf(Table(TableId(1), listOf(TeamId("a"), TeamId("b"))), Table(TableId(2), listOf(TeamId("c"), TeamId("d"))))),
            ),
        )
    }

    private fun tournament(status: TournamentStatus = TournamentStatus.ACTIVE, totalRounds: Int = 4) =
        tournamentRepository.save(
            Tournament(tournamentId, ownerId, "Spring Classic", "", 16, TournamentSettings(totalRounds = totalRounds), status, now, now),
        )

    /** Enters all four games of a round-1 table (the first team wins 70/30) and, when [confirmed], completes it. */
    private fun play(tableNumber: Int, first: String, second: String, confirmed: Boolean = true) {
        transactionTemplate.executeWithoutResult {
            (1..4).forEach { gameNumber ->
                gameRepository.create(baseScoreGame(GameKey(tournamentId, 1, tableNumber, gameNumber), TeamId(first), TeamId(second), 70))
            }
        }
        if (confirmed) {
            listOf(first, second).forEach { confirmationRepository.add(TableKey(tournamentId, 1, tableNumber), TeamId(it), now) }
        }
    }

    private fun completeRoundOne() {
        play(1, "a", "b")
        play(2, "c", "d")
    }

    private fun advance(cookie: Cookie = ownerCookie): ResultActions =
        mockMvc.perform(post("/api/tournaments/${tournamentId.value}/rounds").cookie(cookie))

    private fun roundCount(): Int =
        jdbcTemplate.queryForObject("SELECT count(*) FROM rounds WHERE tournament_id = ?", Int::class.java, tournamentId.value)!!

    @Test
    fun `a complete round advances to the next, answered as the new round`() {
        completeRoundOne()

        // Standings a, c (8 VP, 280 TP each; a before c by name), b, d: a-c, b-d.
        advance()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.roundNumber").value(2))
            .andExpect(jsonPath("$.tables[0].teamIds[0]").value("a"))
            .andExpect(jsonPath("$.tables[0].teamIds[1]").value("c"))
            .andExpect(jsonPath("$.tables[1].teamIds[0]").value("b"))
            .andExpect(jsonPath("$.tables[1].teamIds[1]").value("d"))
            .andExpect(jsonPath("$.tables[0].status").value("PLAYING"))
        assertEquals(2, roundCount())
    }

    @Test
    fun `an incomplete round answers ROUND_INCOMPLETE and changes nothing`() {
        play(1, "a", "b")
        play(2, "c", "d", confirmed = false)

        advance()
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("ROUND_INCOMPLETE"))
        assertEquals(1, roundCount())
    }

    @Test
    fun `the last round answers TOTAL_ROUNDS_REACHED`() {
        tournament(totalRounds = 1)
        completeRoundOne()

        advance()
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("TOTAL_ROUNDS_REACHED"))
    }

    @Test
    fun `a tournament in setup or finished answers TOURNAMENT_NOT_ACTIVE`() {
        for (tournamentStatus in listOf(TournamentStatus.SETUP, TournamentStatus.COMPLETED)) {
            tournament(status = tournamentStatus)

            advance()
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.code").value("TOURNAMENT_NOT_ACTIVE"))
        }
    }

    @Test
    fun `another admin cannot advance the tournament`() {
        completeRoundOne()

        advance(mockMvc.loginAsSeededAdmin(adminUsers, passwordHasher, objectMapper)).andExpect(status().isNotFound)
        assertEquals(1, roundCount())
    }

    @Test
    fun `a concurrent second advance waits for the first and is then rejected as incomplete`() {
        completeRoundOne()
        val advanced = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            // The first advance in flight: holds the tournament lock, round 2 saved but uncommitted.
            val first = executor.submit {
                transactionTemplate.executeWithoutResult {
                    startNextRound.start(tournamentId, ownerId)
                    advanced.countDown()
                    release.await(10, TimeUnit.SECONDS)
                }
            }
            advanced.await(10, TimeUnit.SECONDS)

            val second = executor.submit<Pair<Int, String>> {
                val response = advance().andReturn().response
                response.status to response.contentAsString
            }
            Thread.sleep(500)
            assertFalse(second.isDone, "the second advance blocks on the tournament lock")

            release.countDown()
            first.get(10, TimeUnit.SECONDS)
            val (status, body) = second.get(10, TimeUnit.SECONDS)

            assertEquals(409, status)
            assertEquals("ROUND_INCOMPLETE", objectMapper.readTree(body)["code"].asString())
            assertEquals(2, roundCount(), "exactly one new round")
        } finally {
            release.countDown()
            executor.shutdown()
        }
    }
}
