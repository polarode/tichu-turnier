package com.example.template.infra.rest

import com.example.template.application.api.StartTournament
import com.example.template.domain.AdminUserRepository
import com.example.template.domain.PasswordHasher
import com.example.template.domain.Round
import com.example.template.domain.RoundId
import com.example.template.domain.RoundRepository
import com.example.template.domain.TournamentId
import com.example.template.domain.TournamentRepository
import com.example.template.main.Application
import com.example.template.testsupport.PostgresBackedIntegrationTest
import com.example.template.testsupport.loginAsSeededAdmin
import tools.jackson.databind.ObjectMapper
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

/**
 * Proves the transactional contract of the start flow against real PostgreSQL:
 * a failure mid-way (the round save) must leave NO partial state — no round or
 * table rows, tournament still in `setup`. The unit-level test
 * (`StartTournamentCommandTest`) only proves statement ordering because it has no
 * Spring transaction; this test exercises the real `@Transactional` rollback.
 *
 * The failure is injected by overriding the `RoundRepository` bean with a
 * `@Primary` test bean that throws on save.
 */
@SpringBootTest(classes = [Application::class, StartRollbackIntegrationTest.FailingRoundPersistence::class])
class StartRollbackIntegrationTest(
    @Autowired private val context: WebApplicationContext,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val jdbcTemplate: JdbcTemplate,
    @Autowired private val adminUsers: AdminUserRepository,
    @Autowired private val passwordHasher: PasswordHasher,
    @Autowired private val tournamentRepository: TournamentRepository,
    @Autowired private val startTournament: StartTournament,
) : PostgresBackedIntegrationTest() {

    @TestConfiguration
    class FailingRoundPersistence {
        @Bean
        @Primary
        fun failingRoundRepository(): RoundRepository = object : RoundRepository {
            override fun save(round: Round): Nothing =
                throw IllegalStateException("injected persistence failure")

            override fun findByTournamentAndNumber(tournamentId: TournamentId, number: Int): Nothing =
                throw UnsupportedOperationException()

            override fun findByTournament(tournamentId: TournamentId): Nothing =
                throw UnsupportedOperationException()
        }
    }

    private val mockMvc: MockMvc = MockMvcBuilders.webAppContextSetup(context)
        .apply<org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder>(springSecurity())
        .build()

    private lateinit var sessionCookie: Cookie

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("DELETE FROM tournaments")
        sessionCookie = mockMvc.loginAsSeededAdmin(adminUsers, passwordHasher, objectMapper)
    }

    @Test
    fun `a failed start rolls back - no round rows and the tournament stays in setup`() {
        val tournamentId = createTournamentWithTwoTeams()
        val statusBefore = jdbcTemplate.queryForObject(
            "SELECT status FROM tournaments WHERE id = ?",
            String::class.java,
            tournamentId,
        )!!

        try {
            startTournament.start(TournamentId(tournamentId), tournamentRepository.findById(TournamentId(tournamentId))!!.ownerId)
        } catch (_: IllegalStateException) {
            // expected: the injected round-save failure
        }

        val rounds = jdbcTemplate.queryForObject("SELECT count(*) FROM rounds", Int::class.java)!!
        val tables = jdbcTemplate.queryForObject("SELECT count(*) FROM round_tables", Int::class.java)!!
        val statusAfter = jdbcTemplate.queryForObject(
            "SELECT status FROM tournaments WHERE id = ?",
            String::class.java,
            tournamentId,
        )!!
        assertEquals(0, rounds, "no partial round survived")
        assertEquals(0, tables, "no partial table rows survived")
        assertEquals(statusBefore, statusAfter, "the status transition rolled back")
    }

    /** Creates a tournament with two teams over the real HTTP surface. */
    private fun createTournamentWithTwoTeams(): String {
        val id = mockMvc.perform(
            post("/api/tournaments")
                .cookie(sessionCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Spring Classic","description":"","maxTeams":16}"""),
        )
            .andExpect(status().isCreated)
            .andReturn().response.contentAsString
            .let { (objectMapper.readValue(it, Map::class.java) as Map<*, *>)["id"] as String }
        for (name in listOf("Team Alpha", "Team Beta")) {
            mockMvc.perform(
                post("/api/tournaments/$id/teams")
                    .cookie(sessionCookie)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            mapOf(
                                "name" to name,
                                "playerOneName" to "${name} 1",
                                "playerTwoName" to "${name} 2",
                            ),
                        ),
                    ),
            ).andExpect(status().isCreated)
        }
        return id
    }
}
