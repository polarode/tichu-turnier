package tichu.infra.rest

import tichu.domain.AdminUserRepository
import tichu.domain.PasswordHasher
import tichu.testsupport.PostgresBackedIntegrationTest
import tichu.testsupport.loginAsSeededAdmin
import tools.jackson.databind.ObjectMapper
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Integration test for the anonymous public read `GET /api/public/tournaments` (ADR 0008).
 * Drives the real security chain: the public list is reachable without any session, only
 * public non-SETUP tournaments appear with their team counts, and the rest of the api prefix
 * stays closed to anonymous callers (the admin catch-all under the api prefix).
 */
@SpringBootTest(classes = [tichu.main.Application::class])
class PublicTournamentsIntegrationTest(
    @Autowired private val context: WebApplicationContext,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val jdbcTemplate: JdbcTemplate,
    @Autowired private val adminUsers: AdminUserRepository,
    @Autowired private val passwordHasher: PasswordHasher,
) : PostgresBackedIntegrationTest() {

    private val mockMvc: MockMvc = MockMvcBuilders.webAppContextSetup(context)
        .apply<DefaultMockMvcBuilder>(springSecurity())
        .build()

    private lateinit var sessionCookie: Cookie

    @BeforeEach
    fun authenticate() {
        sessionCookie = mockMvc.loginAsSeededAdmin(adminUsers, passwordHasher, objectMapper)
    }

    @Test
    fun `anonymous caller can read the public tournament list`() {
        mockMvc.perform(get("/api/public/tournaments"))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
    }

    @Test
    fun `lists only public tournaments that are active or published, with their team counts`() {
        val publicActive = createTournament("Spring Open")
        setPublicVisibility(publicActive, true)
        setStatus(publicActive, "ACTIVE")
        createTeam(publicActive, "Alpha")
        createTeam(publicActive, "Bravo")

        val publicPublished = createTournament("Winter Cup")
        setPublicVisibility(publicPublished, true)
        setStatus(publicPublished, "PUBLISHED")
        createTeam(publicPublished, "Charlie")

        val publicSetup = createTournament("Draft Cup")
        setPublicVisibility(publicSetup, true)
        createTeam(publicSetup, "Delta")

        val privateActive = createTournament("Secret Cup")
        setStatus(privateActive, "ACTIVE")
        createTeam(privateActive, "Echo")

        val response = mockMvc.perform(get("/api/public/tournaments"))
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsString
        val items = objectMapper.readTree(response)

        assertEquals(2, items.size(), "only public ACTIVE and PUBLISHED tournaments are listed: $response")
        val byId = items.associateBy({ it["id"].asText() }, { it })
        assertEquals(setOf(publicActive, publicPublished), byId.keys)
        assertEquals("ACTIVE", byId.getValue(publicActive)["status"].asText())
        assertEquals(2, byId.getValue(publicActive)["teamCount"].asInt())
        assertEquals("PUBLISHED", byId.getValue(publicPublished)["status"].asText())
        assertEquals(1, byId.getValue(publicPublished)["teamCount"].asInt())
    }

    @Test
    fun `public list exposes only public facts and no owner or credential fields`() {
        val id = createTournament("Facts Only")
        setPublicVisibility(id, true)
        setStatus(id, "ACTIVE")
        createTeam(id, "Foxtrot")

        val response = mockMvc.perform(get("/api/public/tournaments"))
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsString
        val items = objectMapper.readTree(response)

        val allowedKeys = setOf("id", "name", "status", "teamCount")
        items.forEach { item ->
            assertEquals(allowedKeys, item.propertyNames().toSet(), "unexpected fields in $item")
        }
        val forbiddenFragments = listOf("owner", "token", "credential", "password", "accesslink", "link", "session")
        forbiddenFragments.forEach { fragment ->
            assertFalse(response.lowercase().contains(fragment), "public body must not mention '$fragment': $response")
        }
    }

    @Test
    fun `admin list and team endpoints stay closed to anonymous callers`() {
        mockMvc.perform(get("/api/tournaments"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))

        mockMvc.perform(get("/api/team/me"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
    }

    @Test
    fun `the public permit is GET only, so writes under the public prefix stay closed`() {
        mockMvc.perform(
            post("/api/public/tournaments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Sneaky","maxTeams":16}"""),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
    }

    private fun createTournament(name: String): String {
        val body = mapOf("name" to name, "description" to "", "maxTeams" to 16)
        val response = mockMvc.perform(
            post("/api/tournaments")
                .cookie(sessionCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)),
        )
            .andExpect(status().isCreated)
            .andReturn()
            .response
            .contentAsString
        return objectMapper.readTree(response)["id"].asText()
    }

    private fun setPublicVisibility(id: String, publicVisibility: Boolean) {
        mockMvc.perform(
            put("/api/tournaments/$id/public-visibility")
                .cookie(sessionCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"publicVisibility":$publicVisibility}"""),
        ).andExpect(status().isOk)
    }

    private fun setStatus(id: String, status: String) {
        jdbcTemplate.update("UPDATE tournaments SET status = ? WHERE id = ?", status, id)
    }

    private fun createTeam(tournamentId: String, name: String) {
        mockMvc.perform(
            post("/api/tournaments/$tournamentId/teams")
                .cookie(sessionCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "name" to name,
                            "playerOneName" to "$name 1",
                            "playerTwoName" to "$name 2",
                        ),
                    ),
                ),
        ).andExpect(status().isCreated)
    }
}
