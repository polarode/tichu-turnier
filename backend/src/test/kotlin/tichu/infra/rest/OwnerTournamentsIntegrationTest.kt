package tichu.infra.rest

import tichu.domain.AdminRole
import tichu.domain.AdminUserRepository
import tichu.domain.PasswordHasher
import tichu.testsupport.PostgresBackedIntegrationTest
import tichu.testsupport.loginAndCaptureSession
import tichu.testsupport.seedAdmin
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.util.UUID

/**
 * Seam: the owner's all-tournaments listing (`GET /api/admins/tournaments`). The platform
 * Owner sees every tournament with its lead organizer's email and the LEAD role; a normal
 * admin is refused with 403 FORBIDDEN.
 */
@SpringBootTest(classes = [tichu.main.Application::class])
class OwnerTournamentsIntegrationTest(
    @Autowired private val context: WebApplicationContext,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val adminUsers: AdminUserRepository,
    @Autowired private val passwordHasher: PasswordHasher,
) : PostgresBackedIntegrationTest() {

    private val mockMvc: MockMvc = MockMvcBuilders.webAppContextSetup(context)
        .apply<org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder>(springSecurity())
        .build()

    @Test
    fun `the owner lists every tournament with each lead organizer's email`() {
        val firstEmail = uniqueEmail("first")
        val first = loginNewAdmin(firstEmail)
        val firstId = createTournament(first, "First Cup")

        val secondEmail = uniqueEmail("second")
        val second = loginNewAdmin(secondEmail)
        val secondId = createTournament(second, "Second Cup")

        val owner = loginNewOwner(uniqueEmail("owner"))

        val body = mockMvc.perform(get("/api/admins/tournaments").cookie(owner))
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsString
        val entries = objectMapper.readTree(body)

        assertEquals(firstEmail, leadEmailOf(entries, firstId))
        assertEquals(secondEmail, leadEmailOf(entries, secondId))
    }

    @Test
    fun `a normal admin gets 403 FORBIDDEN on the owner's all-tournaments listing`() {
        val cookie = loginNewAdmin(uniqueEmail("admin"))

        mockMvc.perform(get("/api/admins/tournaments").cookie(cookie))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("FORBIDDEN"))
    }

    private fun leadEmailOf(entries: JsonNode, id: String): String? =
        entries.firstOrNull { it["id"].asText() == id }?.get("leadOrganizerEmail")?.asText()

    private fun createTournament(cookie: Cookie, name: String): String {
        val response: MvcResult = mockMvc.perform(
            post("/api/tournaments").cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"$name","description":"","maxTeams":16}"""),
        )
            .andExpect(status().isCreated)
            .andReturn()
        return objectMapper.readTree(response.response.contentAsString)["id"].asText()
    }

    private fun loginNewAdmin(email: String): Cookie {
        seedAdmin(adminUsers, passwordHasher, email)
        return mockMvc.loginAndCaptureSession(objectMapper, email)
    }

    private fun loginNewOwner(email: String): Cookie {
        seedAdmin(adminUsers, passwordHasher, email, role = AdminRole.OWNER)
        return mockMvc.loginAndCaptureSession(objectMapper, email)
    }

    private fun uniqueEmail(prefix: String) = "$prefix-${UUID.randomUUID()}@example.com"
}
