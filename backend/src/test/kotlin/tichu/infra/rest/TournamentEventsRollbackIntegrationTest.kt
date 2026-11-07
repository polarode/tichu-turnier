package tichu.infra.rest

import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import tichu.domain.Pairing
import tichu.domain.Player
import tichu.domain.PlayerId
import tichu.domain.Round
import tichu.domain.RoundId
import tichu.domain.RoundRepository
import tichu.domain.SessionTokens
import tichu.domain.Table
import tichu.domain.TableConfirmationRepository
import tichu.domain.TableId
import tichu.domain.TableKey
import tichu.domain.Team
import tichu.domain.TeamId
import tichu.domain.TeamRepository
import tichu.domain.TeamToken
import tichu.domain.TeamTokenRepository
import tichu.domain.Tournament
import tichu.domain.TournamentEvent
import tichu.domain.TournamentEvents
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

/**
 * A record that rolls back must emit nothing: the event is published inside the
 * transaction but delivered only after commit. The failure is injected after the
 * real broadcaster has accepted the event (a `@Primary` decorator that delegates,
 * then throws), so only the afterCommit deferral keeps the stream silent.
 */
@SpringBootTest(classes = [Application::class, TournamentEventsRollbackIntegrationTest.FailingAfterPublish::class])
class TournamentEventsRollbackIntegrationTest(
    @Autowired private val context: WebApplicationContext,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val adminUsers: tichu.domain.AdminUserRepository,
    @Autowired private val passwordHasher: tichu.domain.PasswordHasher,
    @Autowired private val teamRepository: TeamRepository,
    @Autowired private val tournamentRepository: TournamentRepository,
    @Autowired private val roundRepository: RoundRepository,
    @Autowired private val confirmationRepository: TableConfirmationRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : PostgresBackedIntegrationTest() {

    @TestConfiguration
    class FailingAfterPublish {
        @Bean
        @Primary
        fun failingTournamentEvents(
            @Qualifier("tournamentEventBroadcaster") delegate: TournamentEvents,
        ): TournamentEvents = object : TournamentEvents {
            override fun publish(event: TournamentEvent) {
                delegate.publish(event)
                throw IllegalStateException("injected failure after publish")
            }
        }
    }

    private val mockMvc: MockMvc = MockMvcBuilders.webAppContextSetup(context)
        .apply<org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder>(springSecurity())
        .build()

    private val tournamentId = "t-rollback"
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
                name = "Rollback Cup",
                description = "",
                maxTeams = 16,
                settings = TournamentSettings(),
                status = TournamentStatus.ACTIVE,
                createdAt = Instant.parse("2026-01-01T12:00:00Z"),
                updatedAt = Instant.parse("2026-01-01T12:00:00Z"),
            ),
        )
        for (id in listOf("team-a", "team-b")) {
            teamRepository.save(
                Team(
                    TeamId(id), TournamentId(tournamentId), id,
                    listOf(
                        Player(PlayerId("$id-p1"), TournamentId(tournamentId), TeamId(id), "$id P1"),
                        Player(PlayerId("$id-p2"), TournamentId(tournamentId), TeamId(id), "$id P2"),
                    ),
                ),
            )
        }
        roundRepository.save(
            Round(
                RoundId("r-1"), TournamentId(tournamentId), 1,
                Pairing(tables = listOf(Table(TableId(1), listOf(TeamId("team-a"), TeamId("team-b"))))),
            ),
        )
        val sessionTokens = context.getBean(SessionTokens::class.java)
        val raw = sessionTokens.generate()
        val now = Instant.now()
        context.getBean(TeamTokenRepository::class.java)
            .save(TeamToken(TeamId("team-a"), sessionTokens.hash(raw), revealed = true, createdAt = now, updatedAt = now))
        val setCookie = mockMvc.perform(
            post("/api/team/auth/exchange").contentType(MediaType.APPLICATION_JSON).content("""{"token":"$raw"}"""),
        ).andExpect(status().isOk).andReturn().response.getHeader(HttpHeaders.SET_COOKIE)!!
        teamCookie = Cookie("team_session", setCookie.substringAfter("team_session=").substringBefore(';'))
    }

    @Test
    fun `a rolled-back record emits no event on the open stream`() {
        val stream = mockMvc.perform(
            get("/api/tournaments/$tournamentId/events").cookie(adminCookie).accept(MediaType.TEXT_EVENT_STREAM),
        ).andExpect(request().asyncStarted()).andReturn()

        // MockMvc rethrows the unmapped failure as a ServletException instead of answering 500.
        assertThrows<jakarta.servlet.ServletException> {
            mockMvc.perform(
            post("/api/team/tournaments/$tournamentId/rounds/1/tables/1/games")
                .cookie(teamCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "gameNumber" to 1,
                            "beschiss" to false,
                            "teams" to listOf(
                                mapOf(
                                    "teamId" to "team-a", "baseScore" to 55,
                                    "players" to listOf(
                                        mapOf("playerId" to "team-a-p1", "position" to 1, "call" to "NONE", "bombs" to 0),
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
        }

        Thread.sleep(500)
        assertFalse(stream.response.contentAsString.contains("GAME_RECORDED"), stream.response.contentAsString)
        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM games", Int::class.java))
    }

    @Test
    fun `a rolled-back round start emits no event on the open stream`() {
        // Round 1 is complete once both teams confirmed its only table, so the next round is allowed.
        listOf("team-a", "team-b").forEach {
            confirmationRepository.add(TableKey(TournamentId(tournamentId), 1, 1), TeamId(it), Instant.now())
        }
        val stream = mockMvc.perform(
            get("/api/tournaments/$tournamentId/events").cookie(adminCookie).accept(MediaType.TEXT_EVENT_STREAM),
        ).andExpect(request().asyncStarted()).andReturn()

        // MockMvc rethrows the unmapped failure as a ServletException instead of answering 500.
        assertThrows<jakarta.servlet.ServletException> {
            mockMvc.perform(
                post("/api/tournaments/$tournamentId/rounds").cookie(adminCookie),
            )
        }

        Thread.sleep(500)
        assertFalse(stream.response.contentAsString.contains("ROUND_STARTED"), stream.response.contentAsString)
        assertEquals(1, jdbcTemplate.queryForObject("SELECT count(*) FROM rounds", Int::class.java))
    }
}
