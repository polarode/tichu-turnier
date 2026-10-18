package com.example.template.infra.rest

import com.example.template.testsupport.PostgresBackedIntegrationTest
import com.example.template.testsupport.registerAndLoginAdmin
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.Cookie
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
 * `*Configuration` classes) against Testcontainers PostgreSQL and drives the team
 * endpoints over MockMvc through the real security chain.
 *
 * Covers the three verbs (create, list, get), the field-anchored and typed error
 * codes, the 401 unauthenticated case and the 404 foreign-tournament case.
 */
@SpringBootTest(classes = [com.example.template.main.Application::class])
class TeamControllerIntegrationTest(
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
    fun `create then fetch a team with its two players`() {
        val tournamentId = createTournament("Spring Classic")

        val teamId = createTeam(tournamentId, "Team Alpha", "Alice", "Bob")

        mockMvc.perform(get("/api/tournaments/$tournamentId/teams/$teamId").cookie(sessionCookie))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(teamId))
            .andExpect(jsonPath("$.tournamentId").value(tournamentId))
            .andExpect(jsonPath("$.name").value("Team Alpha"))
            .andExpect(jsonPath("$.players.length()").value(2))
            .andExpect(jsonPath("$.players[0].name").value("Alice"))
            .andExpect(jsonPath("$.players[0].teamId").value(teamId))
            .andExpect(jsonPath("$.players[1].name").value("Bob"))
    }

    @Test
    fun `list returns the created teams of the tournament`() {
        val tournamentId = createTournament("Spring Classic")
        createTeam(tournamentId, "Team Alpha", "Alice", "Bob")
        createTeam(tournamentId, "Team Beta", "Carol", "Dave")

        mockMvc.perform(get("/api/tournaments/$tournamentId/teams").cookie(sessionCookie))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].name").value("Team Alpha"))
            .andExpect(jsonPath("$[1].name").value("Team Beta"))
    }

    @Test
    fun `create trims the team and player names`() {
        val tournamentId = createTournament("Spring Classic")

        mockMvc.perform(
            post("/api/tournaments/$tournamentId/teams").cookie(sessionCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"  Team Alpha  ","playerOneName":"  Alice  ","playerTwoName":"  Bob  "}"""),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("Team Alpha"))
            .andExpect(jsonPath("$.players[0].name").value("Alice"))
            .andExpect(jsonPath("$.players[1].name").value("Bob"))
    }

    @Test
    fun `create returns 201 with a typed id`() {
        val tournamentId = createTournament("Spring Classic")

        mockMvc.perform(
            post("/api/tournaments/$tournamentId/teams").cookie(sessionCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(teamBody("Team Alpha")),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").isNotEmpty)
    }

    @Test
    fun `create rejects a blank team name with a field-anchored 400`() {
        val tournamentId = createTournament("Spring Classic")

        mockMvc.perform(
            post("/api/tournaments/$tournamentId/teams").cookie(sessionCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"   ","playerOneName":"Alice","playerTwoName":"Bob"}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
            .andExpect(jsonPath("$.field").value("name"))
    }

    @Test
    fun `create rejects a blank player name with a field-anchored 400`() {
        val tournamentId = createTournament("Spring Classic")

        mockMvc.perform(
            post("/api/tournaments/$tournamentId/teams").cookie(sessionCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Team Alpha","playerOneName":"   ","playerTwoName":"Bob"}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
            .andExpect(jsonPath("$.field").value("playerOneName"))
    }

    @Test
    fun `create rejects two identical players with a typed field-anchored 409`() {
        val tournamentId = createTournament("Spring Classic")

        mockMvc.perform(
            post("/api/tournaments/$tournamentId/teams").cookie(sessionCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Team Alpha","playerOneName":"Alice","playerTwoName":"Alice"}"""),
        )
            .andExpect(status().isConflict)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("DUPLICATE_TEAM_PLAYERS"))
            .andExpect(jsonPath("$.field").value("playerTwoName"))
    }

    @Test
    fun `create rejects a duplicate team name with a typed field-anchored 409`() {
        val tournamentId = createTournament("Spring Classic")
        createTeam(tournamentId, "Team Alpha", "Alice", "Bob")

        mockMvc.perform(
            post("/api/tournaments/$tournamentId/teams").cookie(sessionCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(teamBody("Team Alpha", "Carol", "Dave")),
        )
            .andExpect(status().isConflict)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("DUPLICATE_TEAM_NAME"))
            .andExpect(jsonPath("$.field").value("name"))
    }

    @Test
    fun `create rejects a player already on another team with a typed field-anchored 409`() {
        val tournamentId = createTournament("Spring Classic")
        createTeam(tournamentId, "Team Alpha", "Alice", "Bob")

        mockMvc.perform(
            post("/api/tournaments/$tournamentId/teams").cookie(sessionCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(teamBody("Team Beta", "Carol", "Alice")),
        )
            .andExpect(status().isConflict)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("DUPLICATE_PLAYER_NAME"))
            .andExpect(jsonPath("$.field").value("playerTwoName"))
    }

    @Test
    fun `the same team and player names are accepted in another tournament of the same organizer`() {
        val first = createTournament("Spring Classic")
        val second = createTournament("Autumn Cup")

        createTeam(first, "Team Alpha", "Alice", "Bob")

        mockMvc.perform(
            post("/api/tournaments/$second/teams").cookie(sessionCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(teamBody("Team Alpha", "Alice", "Bob")),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("Team Alpha"))

        mockMvc.perform(get("/api/tournaments/$second/teams").cookie(sessionCookie))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
    }

    @Test
    fun `a foreign admin cannot create a team in another admin's tournament`() {
        val tournamentId = createTournament("Alice's tournament")

        mockMvc.perform(
            post("/api/tournaments/$tournamentId/teams").cookie(otherAdminCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(teamBody("Hijacked")),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
    }

    @Test
    fun `a foreign admin cannot read another admin's teams`() {
        val tournamentId = createTournament("Alice's tournament")
        createTeam(tournamentId, "Team Alpha", "Alice", "Bob")

        mockMvc.perform(get("/api/tournaments/$tournamentId/teams").cookie(otherAdminCookie))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))

        mockMvc.perform(get("/api/tournaments/$tournamentId/teams/any-team").cookie(otherAdminCookie))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
    }

    @Test
    fun `fetching an unknown team returns a 404 problem detail`() {
        val tournamentId = createTournament("Spring Classic")

        mockMvc.perform(get("/api/tournaments/$tournamentId/teams/does-not-exist").cookie(sessionCookie))
            .andExpect(status().isNotFound)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
    }

    @Test
    fun `a team id from another tournament fails as not-found`() {
        val first = createTournament("Spring Classic")
        val second = createTournament("Autumn Cup")
        val teamId = createTeam(first, "Team Alpha", "Alice", "Bob")

        mockMvc.perform(get("/api/tournaments/$second/teams/$teamId").cookie(sessionCookie))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
    }

    @Test
    fun `unauthenticated requests get a 401 problem detail for all verbs`() {
        val tournamentId = createTournament("Spring Classic")

        mockMvc.perform(get("/api/tournaments/$tournamentId/teams"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))

        mockMvc.perform(get("/api/tournaments/$tournamentId/teams/any-team"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))

        mockMvc.perform(
            post("/api/tournaments/$tournamentId/teams")
                .contentType(MediaType.APPLICATION_JSON)
                .content(teamBody("Team Alpha")),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
    }

    private fun teamBody(name: String, playerOne: String = "Alice", playerTwo: String = "Bob"): String =
        objectMapper.writeValueAsString(
            mapOf(
                "name" to name,
                "playerOneName" to playerOne,
                "playerTwoName" to playerTwo,
            ),
        )

    private fun createTournament(name: String, cookie: Cookie = sessionCookie): String {
        val response = mockMvc.perform(
            post("/api/tournaments")
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf("name" to name, "description" to "", "maxTeams" to 16, "settings" to mapOf("allowGrandTichu" to true)),
                    ),
                ),
        )
            .andExpect(status().isCreated)
            .andReturn()
            .response
            .contentAsString
        @Suppress("UNCHECKED_CAST")
        return (objectMapper.readValue(response, Map::class.java) as Map<String, Any>)["id"] as String
    }

    private fun createTeam(tournamentId: String, name: String, playerOne: String, playerTwo: String): String {
        val response = mockMvc.perform(
            post("/api/tournaments/$tournamentId/teams")
                .cookie(sessionCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(teamBody(name, playerOne, playerTwo)),
        )
            .andExpect(status().isCreated)
            .andReturn()
            .response
            .contentAsString
        @Suppress("UNCHECKED_CAST")
        return (objectMapper.readValue(response, Map::class.java) as Map<String, Any>)["id"] as String
    }
}
