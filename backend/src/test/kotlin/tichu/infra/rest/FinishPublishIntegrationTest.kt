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
import tichu.domain.SessionTokens
import tichu.domain.Table
import tichu.domain.TableConfirmationRepository
import tichu.domain.TableId
import tichu.domain.TableKey
import tichu.domain.TeamId
import tichu.domain.TeamRepository
import tichu.domain.TeamToken
import tichu.domain.TeamTokenRepository
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
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.context.WebApplicationContext
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

/**
 * Integration test for finishing, publishing and the team's Final Ranking (S7c, #116),
 * over MockMvc and the real security chain against Testcontainers PostgreSQL: the
 * gates on finish, the one-way lifecycle to published, the team read that stays
 * closed until publication, and owner scoping.
 */
@SpringBootTest(classes = [Application::class])
class FinishPublishIntegrationTest(
    @Autowired private val context: WebApplicationContext,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val adminUsers: AdminUserRepository,
    @Autowired private val passwordHasher: PasswordHasher,
    @Autowired private val tournamentRepository: TournamentRepository,
    @Autowired private val teamRepository: TeamRepository,
    @Autowired private val roundRepository: RoundRepository,
    @Autowired private val gameRepository: GameRepository,
    @Autowired private val confirmationRepository: TableConfirmationRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
    @Autowired private val transactionTemplate: TransactionTemplate,
) : PostgresBackedIntegrationTest() {

    private val mockMvc: MockMvc = MockMvcBuilders.webAppContextSetup(context)
        .apply<org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder>(springSecurity())
        .build()

    private val tournamentId = TournamentId("t-finish")
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

    private fun tournament(totalRounds: Int = 4) =
        tournamentRepository.save(
            Tournament(tournamentId, ownerId, "Spring Classic", "", 16, TournamentSettings(totalRounds = totalRounds), TournamentStatus.ACTIVE, now, now),
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

    private fun adminPost(path: String, cookie: Cookie = ownerCookie): ResultActions =
        mockMvc.perform(post("/api/tournaments/${tournamentId.value}/$path").cookie(cookie))

    private fun finish(cookie: Cookie = ownerCookie): ResultActions = adminPost("finish", cookie)

    private fun publish(cookie: Cookie = ownerCookie): ResultActions = adminPost("publish", cookie)

    private fun standings(cookie: Cookie = ownerCookie): ResultActions =
        mockMvc.perform(get("/api/tournaments/${tournamentId.value}/standings").cookie(cookie))

    private fun finalRanking(cookie: Cookie): ResultActions =
        mockMvc.perform(get("/api/team/me/final-ranking").cookie(cookie))

    /** Exchanges a known team token for a team-session cookie. */
    private fun exchangeTeamSession(teamId: String): Cookie {
        val tokenRepository = context.getBean(TeamTokenRepository::class.java)
        val sessionTokens = context.getBean(SessionTokens::class.java)
        val raw = sessionTokens.generate()
        val issuedAt = Instant.now()
        tokenRepository.save(
            TeamToken(
                teamId = TeamId(teamId),
                tokenHash = sessionTokens.hash(raw),
                revealed = true,
                createdAt = issuedAt,
                updatedAt = issuedAt,
            ),
        )
        val result = mockMvc.perform(
            post("/api/team/auth/exchange")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":"$raw"}"""),
        ).andExpect(status().isOk).andReturn()
        val setCookie = result.response.getHeader(HttpHeaders.SET_COOKIE)
            ?: error("exchange did not set a session cookie")
        return Cookie("team_session", setCookie.substringAfter("team_session=").substringBefore(';'))
    }

    /** A game write by team `a` at round 1, table 1 (game 1 already exists, so only the lifecycle can refuse it). */
    private fun teamGameWrite(teamCookie: Cookie): ResultActions {
        val body = objectMapper.writeValueAsString(
            mapOf(
                "gameNumber" to 1,
                "beschiss" to false,
                "version" to 1,
                "teams" to listOf(
                    mapOf(
                        "teamId" to "a",
                        "baseScore" to 55,
                        "players" to listOf(
                            mapOf("playerId" to "a-1", "position" to 1, "call" to "NONE", "bombs" to 0),
                            mapOf("playerId" to "a-2", "position" to 3, "call" to "NONE", "bombs" to 0),
                        ),
                    ),
                    mapOf(
                        "teamId" to "b",
                        "baseScore" to 45,
                        "players" to listOf(
                            mapOf("playerId" to "b-1", "position" to 2, "call" to "NONE", "bombs" to 0),
                            mapOf("playerId" to "b-2", "position" to 4, "call" to "NONE", "bombs" to 0),
                        ),
                    ),
                ),
            ),
        )
        return mockMvc.perform(
            put("/api/team/tournaments/${tournamentId.value}/rounds/1/tables/1/games/1")
                .cookie(teamCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        )
    }

    @Test
    fun `finishing while rounds remain answers ROUNDS_REMAINING`() {
        completeRoundOne()

        finish()
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("ROUNDS_REMAINING"))
        mockMvc.perform(get("/api/tournaments/${tournamentId.value}").cookie(ownerCookie))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
    }

    @Test
    fun `finishing with an unconfirmed table in the last round answers ROUND_INCOMPLETE`() {
        tournament(totalRounds = 1)
        play(1, "a", "b")
        play(2, "c", "d", confirmed = false)

        finish()
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("ROUND_INCOMPLETE"))
    }

    @Test
    fun `a finished tournament publishes once and the team reads the same ranking as the admin`() {
        tournament(totalRounds = 1)
        completeRoundOne()

        finish()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("COMPLETED"))

        // The team session is exchanged after finishing: its link stays valid.
        val teamCookie = exchangeTeamSession("a")
        finalRanking(teamCookie)
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("RESULTS_NOT_PUBLISHED"))
        teamGameWrite(teamCookie)
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("TOURNAMENT_NOT_ACTIVE"))

        publish()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("PUBLISHED"))
        publish()
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("TOURNAMENT_NOT_COMPLETED"))
        finish()
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("TOURNAMENT_NOT_ACTIVE"))

        val adminEntries = objectMapper.readTree(standings().andExpect(status().isOk).andReturn().response.contentAsString)
        val teamBody = finalRanking(teamCookie).andExpect(status().isOk).andReturn().response.contentAsString
        val teamEntries = objectMapper.readTree(teamBody)
        assertEquals(4, adminEntries.size())
        assertEquals(adminEntries, teamEntries)
    }

    @Test
    fun `another admin cannot finish or publish the tournament`() {
        tournament(totalRounds = 1)
        completeRoundOne()
        val intruder = mockMvc.loginAsSeededAdmin(adminUsers, passwordHasher, objectMapper)

        finish(intruder).andExpect(status().isNotFound)
        finish().andExpect(status().isOk)

        publish(intruder).andExpect(status().isNotFound)
        publish().andExpect(status().isOk)
    }
}
