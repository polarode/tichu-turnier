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
 * Seam: the organizer's game correction over HTTP (ADR 0010), driven through
 * MockMvc and the real security chain against Testcontainers PostgreSQL. The team side
 * records and confirms the table; the organizer then corrects one game of the completed
 * table, and the correction is read back through the admin and team reads.
 */
@SpringBootTest(classes = [Application::class])
class GameCorrectionIntegrationTest(
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

    private val tournamentId = "t-correct"
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
    private val organizerGameUrl = "/api/tournaments/$tournamentId/rounds/1/tables/1/games/1"

    /** A game body in the shape both the team PUT and the organizer PUT accept. */
    private fun gameBody(gameNumber: Int = 1, version: Int? = null, baseA: Int = 55): String =
        objectMapper.writeValueAsString(
            mapOf(
                "gameNumber" to gameNumber,
                "beschiss" to false,
                "version" to version,
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

    /** Both teams record all four games and confirm the table, so it is COMPLETED. */
    private fun recordAllGamesAndComplete() {
        (1..4).forEach { gameNumber ->
            mockMvc.perform(
                post(teamGamesUrl).cookie(teamACookie).contentType(MediaType.APPLICATION_JSON)
                    .content(gameBody(gameNumber = gameNumber)),
            ).andExpect(status().isCreated)
        }
        val seen = objectMapper.writeValueAsString(
            mapOf("games" to (1..4).map { mapOf("gameNumber" to it, "version" to 1) }),
        )
        listOf(teamACookie, teamBCookie).forEach { cookie ->
            mockMvc.perform(
                put("$tableUrl/confirmation").cookie(cookie).contentType(MediaType.APPLICATION_JSON).content(seen),
            ).andExpect(status().isOk)
        }
    }

    @Test
    fun `the organizer corrects a completed table's game and the correction reads back for admin and team`() {
        recordAllGamesAndComplete()

        mockMvc.perform(
            put(organizerGameUrl).cookie(organizerCookie).contentType(MediaType.APPLICATION_JSON)
                .content(gameBody(version = 1, baseA = 70)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.version").value(2))
            .andExpect(jsonPath("$.teams[0].baseScore").value(70))
            .andExpect(jsonPath("$.outcome.teamPoints.$teamA").value(70))
            .andExpect(jsonPath("$.correction.correctedBy").value(ownerId.value))
            .andExpect(jsonPath("$.correction.correctedAt").isNotEmpty)

        mockMvc.perform(get("/api/tournaments/$tournamentId/rounds").cookie(organizerCookie))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].tables[0].games[0].teams[0].baseScore").value(70))
            .andExpect(jsonPath("$[0].tables[0].games[0].correction.correctedBy").value(ownerId.value))

        mockMvc.perform(get(teamGamesUrl).cookie(teamACookie))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].teams[0].baseScore").value(70))
            .andExpect(jsonPath("$[0].correction.correctedBy").value(ownerId.value))
            .andExpect(jsonPath("$[0].correction.correctedAt").isNotEmpty)
    }

    @Test
    fun `correcting a game at a table that is still playing answers TABLE_NOT_COMPLETED`() {
        mockMvc.perform(
            post(teamGamesUrl).cookie(teamACookie).contentType(MediaType.APPLICATION_JSON).content(gameBody()),
        ).andExpect(status().isCreated)

        mockMvc.perform(
            put(organizerGameUrl).cookie(organizerCookie).contentType(MediaType.APPLICATION_JSON)
                .content(gameBody(version = 1, baseA = 70)),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("TABLE_NOT_COMPLETED"))
    }

    @Test
    fun `a team session is refused and a stranger admin gets 404 when correcting a game`() {
        recordAllGamesAndComplete()

        mockMvc.perform(
            put(organizerGameUrl).cookie(teamACookie).contentType(MediaType.APPLICATION_JSON)
                .content(gameBody(version = 1, baseA = 70)),
        ).andExpect(status().isUnauthorized)

        val strangerCookie = mockMvc.loginAsSeededAdmin(adminUsers, passwordHasher, objectMapper)
        mockMvc.perform(
            put(organizerGameUrl).cookie(strangerCookie).contentType(MediaType.APPLICATION_JSON)
                .content(gameBody(version = 1, baseA = 70)),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
    }

    @Test
    fun `a stale version answers GAME_CONCURRENT_MODIFICATION`() {
        recordAllGamesAndComplete()
        mockMvc.perform(
            put(organizerGameUrl).cookie(organizerCookie).contentType(MediaType.APPLICATION_JSON)
                .content(gameBody(version = 1, baseA = 70)),
        ).andExpect(status().isOk)

        mockMvc.perform(
            put(organizerGameUrl).cookie(organizerCookie).contentType(MediaType.APPLICATION_JSON)
                .content(gameBody(version = 1, baseA = 30)),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("GAME_CONCURRENT_MODIFICATION"))
    }
}
