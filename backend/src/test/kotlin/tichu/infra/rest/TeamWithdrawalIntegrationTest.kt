package tichu.infra.rest

import tichu.domain.AdminUserId
import tichu.domain.AdminUserRepository
import tichu.domain.Pairing
import tichu.domain.PasswordHasher
import tichu.domain.Round
import tichu.domain.RoundId
import tichu.domain.RoundRepository
import tichu.domain.SessionTokens
import tichu.domain.Table
import tichu.domain.TableId
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
import tichu.testsupport.loginAndCaptureSession
import tichu.testsupport.loginAsSeededAdmin
import tichu.testsupport.seedAdmin
import tichu.testsupport.teamOf
import tools.jackson.databind.ObjectMapper
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.time.Instant
import java.util.UUID

/**
 * Seam: the organizer's Withdrawal of a team over HTTP (ADR 0011), driven through
 * MockMvc and the real security chain against Testcontainers PostgreSQL. A two-team table
 * is played halfway, one team withdraws, and the forfeits, the completed table and the
 * standings are read back through the admin, team and standings reads.
 */
@SpringBootTest(classes = [Application::class])
class TeamWithdrawalIntegrationTest(
    @Autowired private val context: WebApplicationContext,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val adminUsers: AdminUserRepository,
    @Autowired private val passwordHasher: PasswordHasher,
    @Autowired private val teamRepository: TeamRepository,
    @Autowired private val tournamentRepository: TournamentRepository,
    @Autowired private val roundRepository: RoundRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : PostgresBackedIntegrationTest() {

    private val mockMvc: MockMvc = MockMvcBuilders.webAppContextSetup(context)
        .apply<org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder>(springSecurity())
        .build()

    private val tournamentId = "t-withdraw"
    private val teamA = "team-a"
    private val teamB = "team-b"

    private var ownerId = AdminUserId("")
    private lateinit var organizerCookie: Cookie
    private lateinit var teamACookie: Cookie
    private lateinit var teamBCookie: Cookie

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("DELETE FROM tournaments")
        val email = "organizer-${UUID.randomUUID()}@example.com"
        ownerId = seedAdmin(adminUsers, passwordHasher, email).id
        organizerCookie = mockMvc.loginAndCaptureSession(objectMapper, email)

        tournamentRepository.save(
            Tournament(
                id = TournamentId(tournamentId),
                ownerId = ownerId,
                name = "Spring Classic",
                description = "",
                maxTeams = 16,
                settings = TournamentSettings(),
                status = TournamentStatus.ACTIVE,
                createdAt = Instant.parse("2026-01-01T12:00:00Z"),
                updatedAt = Instant.parse("2026-01-01T12:00:00Z"),
            ),
        )
        listOf(teamA, teamB).forEach { teamRepository.save(teamOf(TournamentId(tournamentId), it)) }
        roundRepository.save(
            Round(
                RoundId("r-1"),
                TournamentId(tournamentId),
                1,
                Pairing(listOf(Table(TableId(1), listOf(TeamId(teamA), TeamId(teamB))))),
            ),
        )

        teamACookie = exchangeTeamSession(teamA)
        teamBCookie = exchangeTeamSession(teamB)
    }

    /** Exchanges a freshly minted team token for a team-session cookie. */
    private fun exchangeTeamSession(teamId: String): Cookie {
        val tokenRepository = context.getBean(TeamTokenRepository::class.java)
        val sessionTokens = context.getBean(SessionTokens::class.java)
        val raw = sessionTokens.generate()
        val now = Instant.now()
        tokenRepository.save(
            TeamToken(
                teamId = TeamId(teamId),
                tokenHash = sessionTokens.hash(raw),
                revealed = true,
                createdAt = now,
                updatedAt = now,
            ),
        )
        val result = mockMvc.perform(
            post("/api/team/auth/exchange").contentType(MediaType.APPLICATION_JSON).content("""{"token":"$raw"}"""),
        ).andExpect(status().isOk).andReturn()
        val setCookie = result.response.getHeader(HttpHeaders.SET_COOKIE)
            ?: error("exchange did not set a session cookie")
        return Cookie("team_session", setCookie.substringAfter("team_session=").substringBefore(';'))
    }

    private val tableUrl = "/api/team/tournaments/$tournamentId/rounds/1/tables/1"
    private val teamGamesUrl = "$tableUrl/games"
    private val withdrawalUrl = "/api/tournaments/$tournamentId/teams/$teamB/withdrawal"
    private val roundsUrl = "/api/tournaments/$tournamentId/rounds"
    private val standingsUrl = "/api/tournaments/$tournamentId/standings"

    /** A game body in the shape the team POST accepts; team A wins with [baseA] of the 100 base points. */
    private fun gameBody(gameNumber: Int, baseA: Int = 55): String =
        objectMapper.writeValueAsString(
            mapOf(
                "gameNumber" to gameNumber,
                "beschiss" to false,
                "version" to null,
                "teams" to listOf(
                    mapOf(
                        "teamId" to teamA,
                        "baseScore" to baseA,
                        "players" to listOf(
                            mapOf("playerId" to "$teamA-1", "position" to 1, "call" to "NONE", "bombs" to 0),
                            mapOf("playerId" to "$teamA-2", "position" to 3, "call" to "NONE", "bombs" to 0),
                        ),
                    ),
                    mapOf(
                        "teamId" to teamB,
                        "baseScore" to 100 - baseA,
                        "players" to listOf(
                            mapOf("playerId" to "$teamB-1", "position" to 2, "call" to "NONE", "bombs" to 0),
                            mapOf("playerId" to "$teamB-2", "position" to 4, "call" to "NONE", "bombs" to 0),
                        ),
                    ),
                ),
            ),
        )

    /** Team A records games 1 and 2 of the four scheduled ones; team B has recorded nothing. */
    private fun recordTwoGames() {
        (1..2).forEach { gameNumber ->
            mockMvc.perform(
                post(teamGamesUrl).cookie(teamACookie).contentType(MediaType.APPLICATION_JSON)
                    .content(gameBody(gameNumber)),
            ).andExpect(status().isCreated)
        }
    }

    private fun withdrawTeamB() = mockMvc.perform(
        post(withdrawalUrl).cookie(organizerCookie).contentType(MediaType.APPLICATION_JSON)
            .content("""{"reason":"DISQUALIFIED","compensation":"HALF"}"""),
    )

    @Test
    fun `withdrawing a team mid-table awards its owed games to the opponent and completes the table on confirmation`() {
        recordTwoGames()

        withdrawTeamB()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.reason").value("DISQUALIFIED"))
            .andExpect(jsonPath("$.compensation").value("HALF"))
            .andExpect(jsonPath("$.roundNumber").value(1))

        mockMvc.perform(get(roundsUrl).cookie(organizerCookie))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].tables[0].forfeits.length()").value(2))
            .andExpect(jsonPath("$[0].tables[0].forfeits[0].gameNumber").value(3))
            .andExpect(jsonPath("$[0].tables[0].forfeits[0].withdrawnTeamId").value(teamB))
            .andExpect(jsonPath("$[0].tables[0].forfeits[0].awardedTeamId").value(teamA))
            .andExpect(jsonPath("$[0].tables[0].forfeits[0].victoryPoints").value(1))
            .andExpect(jsonPath("$[0].tables[0].forfeits[0].tichuPoints").value(50))
            .andExpect(jsonPath("$[0].tables[0].forfeits[1].gameNumber").value(4))
            .andExpect(jsonPath("$[0].tables[0].forfeits[1].awardedTeamId").value(teamA))
            .andExpect(jsonPath("$[0].tables[0].withdrawnTeamIds[0]").value(teamB))
            .andExpect(jsonPath("$[0].tables[0].status").value("PLAYING"))

        mockMvc.perform(
            put("$tableUrl/confirmation").cookie(teamACookie).contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"games":[{"gameNumber":1,"version":1},{"gameNumber":2,"version":1}]}""",
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("COMPLETED"))

        mockMvc.perform(get(standingsUrl).cookie(organizerCookie))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].rank").value(1))
            .andExpect(jsonPath("$[0].teamId").value(teamA))
            .andExpect(jsonPath("$[0].gamesPlayed").value(2))
            .andExpect(jsonPath("$[0].victoryPoints").value(6))
            .andExpect(jsonPath("$[0].tichuPoints").value(210))
            .andExpect(jsonPath("$[0].withdrawalReason").doesNotExist())
            .andExpect(jsonPath("$[1].rank").doesNotExist())
            .andExpect(jsonPath("$[1].teamId").value(teamB))
            .andExpect(jsonPath("$[1].withdrawalReason").value("DISQUALIFIED"))
    }

    @Test
    fun `a withdrawn team reads its withdrawal and cannot record games`() {
        recordTwoGames()
        withdrawTeamB().andExpect(status().isOk)

        mockMvc.perform(get("/api/team/me").cookie(teamBCookie))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.withdrawalReason").value("DISQUALIFIED"))

        mockMvc.perform(
            post(teamGamesUrl).cookie(teamBCookie).contentType(MediaType.APPLICATION_JSON)
                .content(gameBody(gameNumber = 3)),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("TEAM_WITHDRAWN"))
    }

    @Test
    fun `undoing a withdrawal before confirmation removes its forfeits`() {
        recordTwoGames()
        withdrawTeamB().andExpect(status().isOk)

        mockMvc.perform(delete(withdrawalUrl).cookie(organizerCookie))
            .andExpect(status().isNoContent)

        mockMvc.perform(get(roundsUrl).cookie(organizerCookie))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].tables[0].forfeits").isEmpty)
            .andExpect(jsonPath("$[0].tables[0].withdrawnTeamIds").isEmpty)
    }

    @Test
    fun `withdrawing an already withdrawn team answers TEAM_ALREADY_WITHDRAWN`() {
        recordTwoGames()
        withdrawTeamB().andExpect(status().isOk)

        withdrawTeamB()
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("TEAM_ALREADY_WITHDRAWN"))
    }

    @Test
    fun `a stranger admin gets 404 when withdrawing a team`() {
        val strangerCookie = mockMvc.loginAsSeededAdmin(adminUsers, passwordHasher, objectMapper)

        mockMvc.perform(
            post(withdrawalUrl).cookie(strangerCookie).contentType(MediaType.APPLICATION_JSON)
                .content("""{"reason":"VOLUNTARY","compensation":"NONE"}"""),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
    }
}
