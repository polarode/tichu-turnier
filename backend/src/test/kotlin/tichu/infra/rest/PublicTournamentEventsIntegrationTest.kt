package tichu.infra.rest

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import tichu.domain.AdminUserRepository
import tichu.domain.PasswordHasher
import tichu.domain.RoundId
import tichu.domain.TableId
import tichu.domain.Tournament
import tichu.domain.TournamentEvent
import tichu.domain.TournamentEventType
import tichu.domain.TournamentId
import tichu.domain.TournamentRepository
import tichu.domain.TournamentSettings
import tichu.domain.TournamentStatus
import tichu.infra.events.SseTournamentEventBroadcaster
import tichu.main.Application
import tichu.testsupport.PostgresBackedIntegrationTest
import tichu.testsupport.seedAdmin
import java.time.Instant

/**
 * Seam: `GET /api/public/tournaments/{id}/events` through the full Spring context and
 * security chain. The anonymous public live stream carries only the spectator-relevant
 * events (ROUND_STARTED, TOURNAMENT_FINISHED, RESULTS_PUBLISHED); a tournament that is
 * not publicly visible, or unknown, answers 404 before any stream opens.
 */
@SpringBootTest(classes = [Application::class])
class PublicTournamentEventsIntegrationTest(
    @Autowired private val context: WebApplicationContext,
    @Autowired private val adminUsers: AdminUserRepository,
    @Autowired private val passwordHasher: PasswordHasher,
    @Autowired private val tournamentRepository: TournamentRepository,
    @Autowired private val broadcaster: SseTournamentEventBroadcaster,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : PostgresBackedIntegrationTest() {

    private val mockMvc: MockMvc = MockMvcBuilders.webAppContextSetup(context)
        .apply<DefaultMockMvcBuilder>(springSecurity())
        .build()

    private val publicId = "t-public-live"
    private val hiddenId = "t-hidden-live"
    private val setupId = "t-setup-live"

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("DELETE FROM tournaments")
        jdbcTemplate.update("DELETE FROM admin_users")
        val owner = seedAdmin(adminUsers, passwordHasher, "owner@example.com")
        save(publicId, owner.id, publicVisibility = true)
        save(hiddenId, owner.id, publicVisibility = false)
        save(setupId, owner.id, publicVisibility = true, status = TournamentStatus.SETUP)
    }

    private fun save(
        id: String,
        ownerId: tichu.domain.AdminUserId,
        publicVisibility: Boolean,
        status: TournamentStatus = TournamentStatus.ACTIVE,
    ) {
        tournamentRepository.save(
            Tournament(
                id = TournamentId(id),
                ownerId = ownerId,
                name = id,
                description = "",
                maxTeams = 16,
                settings = TournamentSettings(),
                status = status,
                createdAt = Instant.parse("2026-01-01T12:00:00Z"),
                updatedAt = Instant.parse("2026-01-01T12:00:00Z"),
                publicVisibility = publicVisibility,
            ),
        )
    }

    private fun eventsUrl(id: String) = "/api/public/tournaments/$id/events"

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
    fun `anonymous caller streams round starts of a public tournament and no other live events`() {
        val stream = mockMvc.perform(get(eventsUrl(publicId)).accept(MediaType.TEXT_EVENT_STREAM))
            .andExpect(request().asyncStarted())
            .andExpect(status().isOk)
            .andReturn()
        val id = TournamentId(publicId)

        broadcaster.publish(TournamentEvent(TournamentEventType.GAME_RECORDED, id, RoundId("r-1"), TableId(1)))
        broadcaster.publish(TournamentEvent(TournamentEventType.TABLE_CONFIRMED, id, RoundId("r-1"), TableId(1)))
        broadcaster.publish(TournamentEvent(TournamentEventType.ROUND_STARTED, id, RoundId("r-2")))

        val content = streamContentWithin(stream, 1000) { it.contains("event:ROUND_STARTED") }
        assertTrue(content.contains("event:ROUND_STARTED"), content)
        assertFalse(content.contains("event:GAME_RECORDED"), content)
        assertFalse(content.contains("event:TABLE_CONFIRMED"), content)
    }

    @Test
    fun `anonymous caller also streams tournament finish and results publication but not confirmations or visibility changes`() {
        val stream = mockMvc.perform(get(eventsUrl(publicId)).accept(MediaType.TEXT_EVENT_STREAM))
            .andExpect(request().asyncStarted())
            .andExpect(status().isOk)
            .andReturn()
        val id = TournamentId(publicId)

        broadcaster.publish(TournamentEvent(TournamentEventType.CONFIRMATION_RETRACTED, id, RoundId("r-1"), TableId(1)))
        broadcaster.publish(TournamentEvent(TournamentEventType.PUBLIC_VISIBILITY_CHANGED, id))
        broadcaster.publish(TournamentEvent(TournamentEventType.ROUND_STARTED, id, RoundId("r-2")))
        broadcaster.publish(TournamentEvent(TournamentEventType.TOURNAMENT_FINISHED, id))
        broadcaster.publish(TournamentEvent(TournamentEventType.RESULTS_PUBLISHED, id))

        val content = streamContentWithin(stream, 1000) {
            it.contains("event:ROUND_STARTED") &&
                it.contains("event:TOURNAMENT_FINISHED") &&
                it.contains("event:RESULTS_PUBLISHED")
        }
        assertTrue(content.contains("event:ROUND_STARTED"), content)
        assertTrue(content.contains("event:TOURNAMENT_FINISHED"), content)
        assertTrue(content.contains("event:RESULTS_PUBLISHED"), content)
        assertFalse(content.contains("event:CONFIRMATION_RETRACTED"), content)
        assertFalse(content.contains("event:PUBLIC_VISIBILITY_CHANGED"), content)
        assertFalse(content.contains("event:GAME_RECORDED"), content)
        assertFalse(content.contains("event:TABLE_CONFIRMED"), content)
    }

    @Test
    fun `a public tournament still in setup answers 404 and opens no stream`() {
        mockMvc.perform(get(eventsUrl(setupId)).accept(MediaType.TEXT_EVENT_STREAM))
            .andExpect(request().asyncNotStarted())
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
    }

    @Test
    fun `a non-public tournament answers 404 and opens no stream`() {
        mockMvc.perform(get(eventsUrl(hiddenId)).accept(MediaType.TEXT_EVENT_STREAM))
            .andExpect(request().asyncNotStarted())
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
    }

    @Test
    fun `an unknown tournament answers 404 and opens no stream`() {
        mockMvc.perform(get(eventsUrl("does-not-exist")).accept(MediaType.TEXT_EVENT_STREAM))
            .andExpect(request().asyncNotStarted())
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
    }
}
