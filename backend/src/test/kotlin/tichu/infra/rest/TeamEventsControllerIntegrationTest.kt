package tichu.infra.rest

import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import tichu.domain.AdminUserId
import tichu.domain.Pairing
import tichu.domain.Player
import tichu.domain.PlayerId
import tichu.domain.Round
import tichu.domain.RoundId
import tichu.domain.RoundRepository
import tichu.domain.SessionTokens
import tichu.domain.Table
import tichu.domain.TableId
import tichu.domain.Team
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
import tichu.testsupport.seedAdmin
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID

/**
 * Seam: `GET /api/team/events` through the full Spring context and security chain
 * (S-live events, #124). The stream is scoped to the team session's tournament only.
 * Games are recorded over the real team API.
 */
@SpringBootTest(classes = [Application::class])
class TeamEventsControllerIntegrationTest(
    @Autowired private val context: WebApplicationContext,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val adminUsers: tichu.domain.AdminUserRepository,
    @Autowired private val passwordHasher: tichu.domain.PasswordHasher,
    @Autowired private val teamRepository: TeamRepository,
    @Autowired private val tournamentRepository: TournamentRepository,
    @Autowired private val roundRepository: RoundRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : PostgresBackedIntegrationTest() {

    private val mockMvc: MockMvc = MockMvcBuilders.webAppContextSetup(context)
        .apply<org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder>(springSecurity())
        .build()

    private val tournamentId = "t-live"
    private val eventsUrl = "/api/team/events"

    private lateinit var adminCookie: Cookie
    private lateinit var teamCookie: Cookie
    private var ownerId: AdminUserId? = null

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("DELETE FROM tournaments")
        jdbcTemplate.update("DELETE FROM admin_users")
        val owner = seedAdmin(adminUsers, passwordHasher, "owner@example.com")
        ownerId = owner.id
        adminCookie = mockMvc.loginAndCaptureSession(objectMapper, "owner@example.com")
        tournamentRepository.save(
            Tournament(
                id = TournamentId(tournamentId),
                ownerId = owner.id,
                name = "Live Cup",
                description = "",
                maxTeams = 16,
                settings = TournamentSettings(),
                status = TournamentStatus.ACTIVE,
                createdAt = Instant.parse("2026-01-01T12:00:00Z"),
                updatedAt = Instant.parse("2026-01-01T12:00:00Z"),
            ),
        )
        teamRepository.save(team(tournamentId, "team-a"))
        teamRepository.save(team(tournamentId, "team-b"))
        roundRepository.save(
            Round(
                id = RoundId("r-1"),
                tournamentId = TournamentId(tournamentId),
                number = 1,
                pairing = Pairing(tables = listOf(Table(TableId(1), listOf(TeamId("team-a"), TeamId("team-b"))))),
            ),
        )
        teamCookie = exchangeTeamSession("team-a")
    }

    private fun team(tournament: String, id: String) = Team(
        id = TeamId(id),
        tournamentId = TournamentId(tournament),
        name = id,
        players = listOf(
            Player(PlayerId("$id-p1"), TournamentId(tournament), TeamId(id), "$id P1"),
            Player(PlayerId("$id-p2"), TournamentId(tournament), TeamId(id), "$id P2"),
        ),
    )

    private fun exchangeTeamSession(teamId: String): Cookie {
        val sessionTokens = context.getBean(SessionTokens::class.java)
        val raw = sessionTokens.generate()
        val now = Instant.now()
        context.getBean(TeamTokenRepository::class.java).save(
            TeamToken(TeamId(teamId), sessionTokens.hash(raw), revealed = true, createdAt = now, updatedAt = now),
        )
        val result = mockMvc.perform(
            post("/api/team/auth/exchange").contentType(MediaType.APPLICATION_JSON).content("""{"token":"$raw"}"""),
        ).andExpect(status().isOk).andReturn()
        val setCookie = result.response.getHeader(HttpHeaders.SET_COOKIE) ?: error("no session cookie")
        return Cookie("team_session", setCookie.substringAfter("team_session=").substringBefore(';'))
    }

    private fun openStream(cookie: Cookie = teamCookie): MvcResult =
        mockMvc.perform(get(eventsUrl).cookie(cookie).accept(MediaType.TEXT_EVENT_STREAM))
            .andExpect(request().asyncStarted())
            .andReturn()

    private val gamesUrl = "/api/team/tournaments/$tournamentId/rounds/1/tables/1/games"

    private fun recordGame(request: MockHttpServletRequestBuilder = post(gamesUrl), version: Int? = null) = mockMvc.perform(
        request
            .cookie(teamCookie)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                objectMapper.writeValueAsString(
                    mapOf(
                        "gameNumber" to 1,
                        "beschiss" to false,
                        "version" to version,
                        "teams" to listOf(
                            mapOf(
                                "teamId" to "team-a", "baseScore" to 55,
                                "players" to listOf(
                                    mapOf("playerId" to "team-a-p1", "position" to 1, "call" to "SMALL", "bombs" to 0),
                                    mapOf("playerId" to "team-a-p2", "position" to 3, "call" to "NONE", "bombs" to 0),
                                ),
                            ),
                            mapOf(
                                "teamId" to "team-b", "baseScore" to 45,
                                "players" to listOf(
                                    mapOf("playerId" to "team-b-p1", "position" to 2, "call" to "NONE", "bombs" to 0),
                                    mapOf("playerId" to "team-b-p2", "position" to 4, "call" to "NONE", "bombs" to 0),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
    )

    private fun streamContentWithin(result: MvcResult, millis: Long, until: (String) -> Boolean): String {
        val deadline = System.nanoTime() + millis * 1_000_000
        var content = result.response.contentAsString
        while (!until(content) && System.nanoTime() < deadline) {
            Thread.sleep(25)
            content = result.response.contentAsString
        }
        return content
    }

    @Test
    fun `a team opens its tournament's event stream with proxy-safe headers`() {
        mockMvc.perform(get(eventsUrl).cookie(teamCookie).accept(MediaType.TEXT_EVENT_STREAM))
            .andExpect(request().asyncStarted())
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, org.hamcrest.Matchers.startsWith("text/event-stream")))
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache"))
            .andExpect(header().string("X-Accel-Buffering", "no"))
    }

    @Test
    fun `a recorded game appears on the team stream as a thin GAME_RECORDED event within a second`() {
        val stream = openStream()

        recordGame().andExpect(status().isCreated)

        val content = streamContentWithin(stream, 1000) { it.contains("event:GAME_RECORDED") && it.contains("data:") }
        assertTrue(content.contains("event:GAME_RECORDED"), content)
        val data = objectMapper.readTree(content.lineSequence().first { it.startsWith("data:") }.removePrefix("data:"))
        // A thin signal: exactly these ids, no scores or standings (ADR 0007).
        assertEquals(setOf("type", "tournamentId", "roundId", "tableId"), data.propertyNames().toSet())
        assertEquals(tournamentId, data["tournamentId"].asString())
        assertEquals("r-1", data["roundId"].asString())
        assertEquals(1, data["tableId"].asInt())
    }

    @Test
    fun `an organizer change is not sent to the team stream, but the game after it is`() {
        val stream = openStream()
        val coEmail = "team-stream-co-${UUID.randomUUID()}@example.com"
        seedAdmin(adminUsers, passwordHasher, coEmail)

        mockMvc.perform(
            post("/api/tournaments/$tournamentId/organizers").cookie(adminCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$coEmail"}"""),
        ).andExpect(status().isCreated)
        recordGame().andExpect(status().isCreated)

        val content = streamContentWithin(stream, 1000) { it.contains("event:GAME_RECORDED") }
        assertTrue(content.contains("event:GAME_RECORDED"), content)
        assertFalse(content.contains("ORGANIZERS_CHANGED"), content)
    }

    @Test
    fun `a team of another tournament does not receive the event`() {
        val otherTournamentId = "t-other"
        tournamentRepository.save(
            Tournament(
                id = TournamentId(otherTournamentId),
                ownerId = ownerId!!,
                name = "Other Cup",
                description = "",
                maxTeams = 16,
                settings = TournamentSettings(),
                status = TournamentStatus.ACTIVE,
                createdAt = Instant.parse("2026-01-01T12:00:00Z"),
                updatedAt = Instant.parse("2026-01-01T12:00:00Z"),
            ),
        )
        teamRepository.save(team(otherTournamentId, "team-c"))
        val otherTeamCookie = exchangeTeamSession("team-c")

        val ownStream = openStream(teamCookie)
        val otherStream = openStream(otherTeamCookie)

        recordGame().andExpect(status().isCreated)

        val ownContent = streamContentWithin(ownStream, 1000) { it.contains("event:GAME_RECORDED") }
        assertTrue(ownContent.contains("event:GAME_RECORDED"), ownContent)
        val otherContent = streamContentWithin(otherStream, 200) { it.contains("event:GAME_RECORDED") }
        assertFalse(otherContent.contains("event:GAME_RECORDED"), otherContent)
    }

    @Test
    fun `anonymous gets 401 and an admin session gets 403 on the team stream - team endpoints are team-only`() {
        mockMvc.perform(get(eventsUrl).accept(MediaType.TEXT_EVENT_STREAM))
            .andExpect(status().isUnauthorized)
        mockMvc.perform(get(eventsUrl).cookie(adminCookie).accept(MediaType.TEXT_EVENT_STREAM))
            .andExpect(status().isForbidden)
    }
}
