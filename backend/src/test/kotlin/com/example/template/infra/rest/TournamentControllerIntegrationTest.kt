package com.example.template.infra.rest

import com.example.template.testsupport.PostgresBackedIntegrationTest
import com.example.template.testsupport.registerAndLoginAdmin
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.Cookie
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

/**
 * Integration test: boots the real application context (all beans wired through the
 * `*Configuration` classes) against Testcontainers PostgreSQL and drives the
 * controller over MockMvc through the real security chain.
 */
@SpringBootTest(classes = [com.example.template.main.Application::class])
class TournamentControllerIntegrationTest(
    @Autowired private val context: WebApplicationContext,
    @Autowired private val objectMapper: ObjectMapper,
) : PostgresBackedIntegrationTest() {

    private val mockMvc: MockMvc = MockMvcBuilders.webAppContextSetup(context)
        .apply<org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder>(springSecurity())
        .build()

    private lateinit var sessionCookie: Cookie
    private lateinit var otherAdminCookie: Cookie

    @BeforeEach
    fun authenticate() {
        sessionCookie = mockMvc.registerAndLoginAdmin(objectMapper)
        otherAdminCookie = mockMvc.registerAndLoginAdmin(objectMapper)
    }

    @Test
    fun `create then fetch a tournament`() {
        val id = createTournament("Spring Classic", "annual invitational")

        mockMvc.perform(get("/api/tournaments/$id").cookie(sessionCookie))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Spring Classic"))
            .andExpect(jsonPath("$.status").value("SETUP"))
            .andExpect(jsonPath("$.maxTeams").value(16))
            .andExpect(jsonPath("$.settings.allowGrandTichu").value(true))
    }

    @Test
    fun `create persists the grand tichu toggle from the request body`() {
        val id = createTournament("No Grand Tichu", "", allowGrandTichu = false)

        mockMvc.perform(get("/api/tournaments/$id").cookie(sessionCookie))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.settings.allowGrandTichu").value(false))
    }

    @Test
    fun `list returns created tournaments`() {
        createTournament("A")
        createTournament("B")

        mockMvc.perform(get("/api/tournaments").cookie(sessionCookie))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.length()").value(2))
    }

    @Test
    fun `create rejects a blank name with a field-anchored 400`() {
        mockMvc.perform(
            post("/api/tournaments")
                .cookie(sessionCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"  ","maxTeams":16}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
            .andExpect(jsonPath("$.field").value("name"))
    }

    @Test
    fun `create rejects a missing or non-positive maxTeams with a field-anchored 400`() {
        mockMvc.perform(
            post("/api/tournaments")
                .cookie(sessionCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Classic","maxTeams":0}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
            .andExpect(jsonPath("$.field").value("maxTeams"))

        mockMvc.perform(
            post("/api/tournaments")
                .cookie(sessionCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Classic"}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
            .andExpect(jsonPath("$.field").value("maxTeams"))
    }

    @Test
    fun `fetching an unknown tournament returns a 404 problem detail`() {
        mockMvc.perform(get("/api/tournaments/does-not-exist").cookie(sessionCookie))
            .andExpect(status().isNotFound)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
    }

    @Test
    fun `a foreign admin cannot read another admin's tournament`() {
        val id = createTournament("Alice's tournament", "private")

        mockMvc.perform(get("/api/tournaments/$id").cookie(otherAdminCookie))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))

        // The owner's tournament is untouched throughout.
        mockMvc.perform(get("/api/tournaments/$id").cookie(sessionCookie))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Alice's tournament"))
    }

    @Test
    fun `list returns only the caller's own tournaments`() {
        createTournament("alice-only")
        createTournamentAs(otherAdminCookie, "bob-only")

        mockMvc.perform(get("/api/tournaments").cookie(otherAdminCookie))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("bob-only")))
            .andExpect(content().string(not(containsString("alice-only"))))
    }

    @Test
    fun `unauthenticated requests get a 401 problem detail`() {
        mockMvc.perform(get("/api/tournaments"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
    }

    private fun createTournament(
        name: String,
        description: String = "",
        allowGrandTichu: Boolean = true,
    ): String = createTournamentAs(sessionCookie, name, description, allowGrandTichu)

    private fun createTournamentAs(
        cookie: Cookie,
        name: String,
        description: String = "",
        allowGrandTichu: Boolean = true,
    ): String {
        val body = mapOf(
            "name" to name,
            "description" to description,
            "maxTeams" to 16,
            "settings" to mapOf("allowGrandTichu" to allowGrandTichu),
        )
        val response = mockMvc.perform(
            post("/api/tournaments")
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)),
        )
            .andExpect(status().isCreated)
            .andReturn()
            .response
            .contentAsString
        @Suppress("UNCHECKED_CAST")
        return (objectMapper.readValue(response, Map::class.java) as Map<String, Any>)["id"] as String
    }
}
