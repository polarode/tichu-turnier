package tichu.infra.rest

import jakarta.servlet.AsyncEvent
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockAsyncContext
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
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
import tichu.domain.Table
import tichu.domain.TableId
import tichu.domain.Team
import tichu.domain.TeamId
import tichu.domain.TeamRepository
import tichu.domain.TeamToken
import tichu.domain.TeamTokenRepository
import tichu.domain.SessionTokens
import tichu.domain.Tournament
import tichu.domain.TournamentId
import tichu.domain.TournamentRepository
import tichu.domain.TournamentSettings
import tichu.domain.TournamentStatus
import tichu.infra.events.SseTournamentEventBroadcaster
import tichu.main.Application
import tichu.testsupport.PostgresBackedIntegrationTest
import tichu.testsupport.insertAdminRow
import tichu.testsupport.loginAndCaptureSession
import tichu.testsupport.seedAdmin
import tools.jackson.databind.ObjectMapper
import java.time.Instant

/**
 * Seam: `GET /api/tournaments/{id}/events` through the full Spring context and security
 * chain (S-live events, #122). Games are recorded over the real team API.
 */
@SpringBootTest(classes = [Application::class])
class TournamentEventsControllerIntegrationTest(
    @Autowired private val context: WebApplicationContext,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val adminUsers: tichu.domain.AdminUserRepository,
    @Autowired private val passwordHasher: tichu.domain.PasswordHasher,
    @Autowired private val teamRepository: TeamRepository,
    @Autowired private val tournamentRepository: TournamentRepository,
    @Autowired private val roundRepository: RoundRepository,
    @Autowired private val broadcaster: SseTournamentEventBroadcaster,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : PostgresBackedIntegrationTest() {

    private val mockMvc: MockMvc = MockMvcBuilders.webAppContextSetup(context)
        .apply<org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder>(springSecurity())
        .build()

    private val tournamentId = "t-live"
    private val eventsUrl = "/api/tournaments/$tournamentId/events"

    private lateinit var adminCookie: Cookie
    private lateinit var teamCookie: Cookie

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("DELETE FROM tournaments")
        jdbcTemplate.update("DELETE FROM admin_users")
        val owner = seedAdmin(adminUsers, passwordHasher, "owner@example.com")
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
        teamRepository.save(team("team-a"))
        teamRepository.save(team("team-b"))
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

    private fun team(id: String) = Team(
        id = TeamId(id),
        tournamentId = TournamentId(tournamentId),
        name = id,
        players = listOf(
            Player(PlayerId("$id-p1"), TournamentId(tournamentId), TeamId(id), "$id P1"),
            Player(PlayerId("$id-p2"), TournamentId(tournamentId), TeamId(id), "$id P2"),
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

    private fun openStream(): MvcResult =
        mockMvc.perform(get(eventsUrl).cookie(adminCookie).accept(MediaType.TEXT_EVENT_STREAM))
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
    fun `the owning admin opens an event stream with proxy-safe headers`() {
        mockMvc.perform(get(eventsUrl).cookie(adminCookie).accept(MediaType.TEXT_EVENT_STREAM))
            .andExpect(request().asyncStarted())
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, org.hamcrest.Matchers.startsWith("text/event-stream")))
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache"))
            .andExpect(header().string("X-Accel-Buffering", "no"))
    }

    @Test
    fun `a recorded game appears on the open stream as a thin GAME_RECORDED event`() {
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
    fun `an edited game appears on the open stream as GAME_RECORDED`() {
        recordGame().andExpect(status().isCreated)
        val stream = openStream()

        recordGame(put("$gamesUrl/1"), version = 1).andExpect(status().isOk)

        val content = streamContentWithin(stream, 1000) { it.contains("event:GAME_RECORDED") }
        assertTrue(content.contains("event:GAME_RECORDED"), content)
    }

    @Test
    fun `another admin gets 404, a team session 401 and anonymous 401`() {
        seedAdmin(adminUsers, passwordHasher, "other@example.com")
        val otherCookie = mockMvc.loginAndCaptureSession(objectMapper, "other@example.com")

        mockMvc.perform(get(eventsUrl).cookie(otherCookie).accept(MediaType.TEXT_EVENT_STREAM))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
        mockMvc.perform(get(eventsUrl).cookie(teamCookie).accept(MediaType.TEXT_EVENT_STREAM))
            .andExpect(status().isUnauthorized)
        mockMvc.perform(get(eventsUrl).accept(MediaType.TEXT_EVENT_STREAM))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `a timed-out stream passes the async dispatch through the security chain and is released`() {
        // Other tests leave their streams open in the shared context, so count relative.
        val before = broadcaster.openStreamCount(TournamentId(tournamentId))
        val stream = openStream()
        assertEquals(before + 1, broadcaster.openStreamCount(TournamentId(tournamentId)))

        val asyncContext = stream.request.asyncContext as MockAsyncContext
        asyncContext.listeners.toList().forEach { it.onTimeout(AsyncEvent(asyncContext)) }

        mockMvc.perform(asyncDispatch(stream)).andExpect(status().isOk)
        assertEquals(before, broadcaster.openStreamCount(TournamentId(tournamentId)))
    }
}
