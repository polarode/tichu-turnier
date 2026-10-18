package com.example.template.infra.rest

import com.example.template.domain.SessionTokens
import com.example.template.domain.TeamId
import com.example.template.domain.TeamRepository
import com.example.template.domain.TeamToken
import com.example.template.domain.TeamTokenRepository
import com.example.template.domain.TournamentRepository
import com.example.template.domain.TournamentStatus
import com.example.template.testsupport.PostgresBackedIntegrationTest
import com.example.template.testsupport.loginAsSeededAdmin
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
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
import java.time.Instant

/**
 * Integration test for the team magic-link spine (#24), driven over MockMvc and
 * the real security chain against Testcontainers PostgreSQL: the exchange turns a
 * raw link token into an httpOnly team-session cookie, the session is scoped to
 * the team's own data, admin endpoints reject team sessions and vice versa, and
 * every dead link gets the same generic rejection.
 */
@SpringBootTest(classes = [com.example.template.main.Application::class])
class TeamAccessIntegrationTest(
    @Autowired private val context: WebApplicationContext,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val adminUsers: com.example.template.domain.AdminUserRepository,
    @Autowired private val passwordHasher: com.example.template.domain.PasswordHasher,
    @Autowired private val teamTokens: TeamTokenRepository,
    @Autowired private val sessionTokens: SessionTokens,
    @Autowired private val teamRepository: TeamRepository,
    @Autowired private val tournamentRepository: TournamentRepository,
) : PostgresBackedIntegrationTest() {

    private val mockMvc: MockMvc = MockMvcBuilders.webAppContextSetup(context)
        .apply<org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder>(springSecurity())
        .build()

    private lateinit var adminCookie: Cookie

    private fun authenticate() {
        adminCookie = mockMvc.loginAsSeededAdmin(adminUsers, passwordHasher, objectMapper)
    }

    private fun createTournament(name: String): String {
        val response = mockMvc.perform(
            post("/api/tournaments")
                .cookie(adminCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf("name" to name, "description" to "", "maxTeams" to 16, "settings" to mapOf("allowGrandTichu" to true)),
                    ),
                ),
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        return field(response, "id")
    }

    private fun createTeam(tournamentId: String, name: String): String {
        val result = mockMvc.perform(
            post("/api/tournaments/$tournamentId/teams")
                .cookie(adminCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("name" to name, "playerOneName" to "Alice", "playerTwoName" to "Bob"))),
        ).andExpect(status().isCreated).andReturn()
        return field(result.response.contentAsString, "id")
    }

    /** Rotates the team's token to known material so tests can exchange it (#25 adds the real reveal). */
    private fun seedKnownToken(teamId: String): String {
        val raw = sessionTokens.generate()
        val now = Instant.now()
        teamTokens.save(
            TeamToken(
                teamId = TeamId(teamId),
                tokenHash = sessionTokens.hash(raw),
                revealed = true,
                createdAt = now,
                updatedAt = now,
            ),
        )
        return raw
    }

    private fun exchange(token: String): MvcResult = mockMvc.perform(
        post("/api/team/auth/exchange")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"token":"$token"}"""),
    ).andReturn()

    private fun teamCookie(result: MvcResult): Cookie {
        val setCookie = result.response.getHeader(HttpHeaders.SET_COOKIE)
            ?: error("exchange did not set a session cookie (status ${result.response.status})")
        val token = setCookie.substringAfter("team_session=").substringBefore(';')
        return Cookie("team_session", token)
    }

    private fun field(json: String, name: String): String =
        (objectMapper.readValue(json, Map::class.java) as Map<*, *>)[name] as String

    @Test
    fun `a created team has exactly one hashed token and no response carries the raw material`() {
        authenticate()
        val tournamentId = createTournament("Spring Classic")

        val result = mockMvc.perform(
            post("/api/tournaments/$tournamentId/teams")
                .cookie(adminCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("name" to "Team Alpha", "playerOneName" to "Alice", "playerTwoName" to "Bob"))),
        ).andExpect(status().isCreated).andReturn()

        val teamId = field(result.response.contentAsString, "id")
        val token = teamTokens.findByTeamId(TeamId(teamId)) ?: error("created team has no token")
        org.junit.jupiter.api.Assertions.assertTrue(token.tokenHash.length == 64, "only a digest is stored")
        org.junit.jupiter.api.Assertions.assertFalse(token.revealed)
        // The raw material is only ever generated, hashed and dropped — nothing in
        // any response or listing could carry it because the API never returns one.
        org.junit.jupiter.api.Assertions.assertFalse(result.response.contentAsString.contains("tokenHash"))
    }

    @Test
    fun `exchange sets an httpOnly secure same-site cookie and never echoes the token`() {
        authenticate()
        val tournamentId = createTournament("Spring Classic")
        val teamId = createTeam(tournamentId, "Team Alpha")
        val raw = seedKnownToken(teamId)

        val result = exchange(raw).also { org.junit.jupiter.api.Assertions.assertEquals(200, it.response.status) }

        val cookie = result.response.getHeader(HttpHeaders.SET_COOKIE)
        org.junit.jupiter.api.Assertions.assertTrue(cookie!!.contains("HttpOnly"), "cookie is httpOnly: $cookie")
        org.junit.jupiter.api.Assertions.assertTrue(cookie.contains("Secure"), "cookie is Secure: $cookie")
        org.junit.jupiter.api.Assertions.assertTrue(cookie.contains("SameSite=Lax"), "cookie is SameSite=Lax: $cookie")
        org.junit.jupiter.api.Assertions.assertFalse(
            result.response.contentAsString.contains(raw),
            "the raw token must not appear in the response body",
        )
    }

    @Test
    fun `both team members can exchange the same link and each gets a scoped session`() {
        authenticate()
        val tournamentId = createTournament("Spring Classic")
        val teamId = createTeam(tournamentId, "Team Alpha")
        val raw = seedKnownToken(teamId)

        val first = teamCookie(exchange(raw).also { org.junit.jupiter.api.Assertions.assertEquals(200, it.response.status) })
        val second = teamCookie(exchange(raw).also { org.junit.jupiter.api.Assertions.assertEquals(200, it.response.status) })

        val firstMe = mockMvc.perform(get("/api/team/me").cookie(first))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(teamId))
            .andExpect(jsonPath("$.name").value("Team Alpha"))
        org.junit.jupiter.api.Assertions.assertNotNull(firstMe)

        mockMvc.perform(get("/api/team/me").cookie(second))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(teamId))
    }

    @Test
    fun `unknown, revoked and completed-tournament tokens all get the same generic rejection`() {
        authenticate()
        val tournamentId = createTournament("Spring Classic")
        val teamId = createTeam(tournamentId, "Team Alpha")
        val raw = seedKnownToken(teamId)

        fun assertRejected(result: MvcResult) {
            org.junit.jupiter.api.Assertions.assertEquals(401, result.response.status)
            org.junit.jupiter.api.Assertions.assertEquals("TEAM_ACCESS_INVALID", field(result.response.contentAsString, "code"))
        }

        assertRejected(exchange("no-such-token"))

        val stored = teamTokens.findByTeamId(TeamId(teamId))!!
        teamTokens.save(stored.copy(revokedAt = Instant.now(), updatedAt = Instant.now()))
        assertRejected(exchange(raw))

        teamTokens.save(stored.copy(revokedAt = null, updatedAt = Instant.now()))
        tournamentRepository.save(
            tournamentRepository.findById(com.example.template.domain.TournamentId(tournamentId))!!
                .copy(status = TournamentStatus.COMPLETED),
        )
        assertRejected(exchange(raw))
    }

    @Test
    fun `a team session cannot reach another team's data or any admin endpoint`() {
        authenticate()
        val tournamentId = createTournament("Spring Classic")
        val teamId = createTeam(tournamentId, "Team Alpha")
        val raw = seedKnownToken(teamId)
        val teamCookie = teamCookie(exchange(raw).also { org.junit.jupiter.api.Assertions.assertEquals(200, it.response.status) })

        // The only team-scoped read is the principal's own team — there is no way
        // to name another team id at all.
        mockMvc.perform(get("/api/team/me").cookie(teamCookie))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(teamId))

        // Admin endpoints (list/get teams, tournaments) reject the team principal.
        mockMvc.perform(get("/api/tournaments/$tournamentId/teams").cookie(teamCookie))
            .andExpect(status().isForbidden)
        mockMvc.perform(get("/api/tournaments").cookie(teamCookie))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `an admin session cannot reach team endpoints`() {
        authenticate()
        mockMvc.perform(get("/api/team/me").cookie(adminCookie))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `exchange without a token is rejected and the endpoint is reachable unauthenticated`() {
        authenticate()
        mockMvc.perform(
            post("/api/team/auth/exchange")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"token":""}"""),
        ).andExpect(status().isBadRequest)
    }

    // ------------------------------------------------------- access link lifecycle

    @Test
    fun `reveal returns the copyable link exactly once - a second reveal is rejected`() {
        authenticate()
        val tournamentId = createTournament("Spring Classic")
        val teamId = createTeam(tournamentId, "Team Alpha")

        val result = mockMvc.perform(post("/api/tournaments/$tournamentId/teams/$teamId/access-link/reveal").cookie(adminCookie))
            .andExpect(status().isOk)
            .andReturn()
        val body = result.response.contentAsString
        val raw = field(body, "token")
        org.junit.jupiter.api.Assertions.assertTrue(
            field(body, "link").endsWith("/team/login?token=" + java.net.URLEncoder.encode(raw, "UTF-8")),
            "the response carries the copyable login URL: ${field(body, "link")}",
        )

        // The raw material exists only here; the stored row keeps only its digest.
        val stored = teamTokens.findByTeamId(TeamId(teamId))!!
        org.junit.jupiter.api.Assertions.assertEquals(sessionTokens.hash(raw), stored.tokenHash)
        org.junit.jupiter.api.Assertions.assertTrue(stored.revealed)

        mockMvc.perform(post("/api/tournaments/$tournamentId/teams/$teamId/access-link/reveal").cookie(adminCookie))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("LINK_ALREADY_REVEALED"))
    }

    @Test
    fun `rotate kills the old link and its live sessions - the new link is shown once and works`() {
        authenticate()
        val tournamentId = createTournament("Spring Classic")
        val teamId = createTeam(tournamentId, "Team Alpha")
        val oldRaw = reveal(teamId, tournamentId)
        val oldCookie = teamCookie(exchange(oldRaw).also { org.junit.jupiter.api.Assertions.assertEquals(200, it.response.status) })

        mockMvc.perform(post("/api/tournaments/$tournamentId/teams/$teamId/access-link/rotate").cookie(adminCookie))
            .andExpect(status().isNoContent)

        // Old link dead, old session dead.
        org.junit.jupiter.api.Assertions.assertEquals(401, exchange(oldRaw).response.status)
        mockMvc.perform(get("/api/team/me").cookie(oldCookie)).andExpect(status().isUnauthorized)

        // Reveal was reset: the new link is shown once and exchanges.
        val newRaw = reveal(teamId, tournamentId)
        org.junit.jupiter.api.Assertions.assertNotEquals(oldRaw, newRaw)
        org.junit.jupiter.api.Assertions.assertEquals(200, exchange(newRaw).response.status)
    }

    @Test
    fun `revoke stops exchanges and kills live sessions - idempotently`() {
        authenticate()
        val tournamentId = createTournament("Spring Classic")
        val teamId = createTeam(tournamentId, "Team Alpha")
        val raw = reveal(teamId, tournamentId)
        val cookie = teamCookie(exchange(raw).also { org.junit.jupiter.api.Assertions.assertEquals(200, it.response.status) })

        mockMvc.perform(post("/api/tournaments/$tournamentId/teams/$teamId/access-link/revoke").cookie(adminCookie))
            .andExpect(status().isNoContent)
        mockMvc.perform(post("/api/tournaments/$tournamentId/teams/$teamId/access-link/revoke").cookie(adminCookie))
            .andExpect(status().isNoContent)

        org.junit.jupiter.api.Assertions.assertEquals(401, exchange(raw).response.status)
        mockMvc.perform(get("/api/team/me").cookie(cookie)).andExpect(status().isUnauthorized)
    }

    @Test
    fun `no endpoint other than reveal returns token material and unauthenticated reveal is rejected`() {
        authenticate()
        val tournamentId = createTournament("Spring Classic")
        val createResponse = mockMvc.perform(
            post("/api/tournaments/$tournamentId/teams")
                .cookie(adminCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("name" to "Team Alpha", "playerOneName" to "Alice", "playerTwoName" to "Bob"))),
        ).andReturn().response.contentAsString
        val teamId = field(createResponse, "id")

        org.junit.jupiter.api.Assertions.assertFalse(createResponse.contains("token"))
        val list = mockMvc.perform(get("/api/tournaments/$tournamentId/teams").cookie(adminCookie))
            .andExpect(status().isOk).andReturn().response.contentAsString
        org.junit.jupiter.api.Assertions.assertFalse(list.contains("token"))
        val detail = mockMvc.perform(get("/api/tournaments/$tournamentId/teams/$teamId").cookie(adminCookie))
            .andExpect(status().isOk).andReturn().response.contentAsString
        org.junit.jupiter.api.Assertions.assertFalse(detail.contains("token"))

        mockMvc.perform(post("/api/tournaments/$tournamentId/teams/$teamId/access-link/reveal"))
            .andExpect(status().isUnauthorized)
    }

    private fun reveal(teamId: String, tournamentId: String): String =
        field(
            mockMvc.perform(post("/api/tournaments/$tournamentId/teams/$teamId/access-link/reveal").cookie(adminCookie))
                .andExpect(status().isOk)
                .andReturn().response.contentAsString,
            "token",
        )
}