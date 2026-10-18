package com.example.template.infra.rest

import com.example.template.testsupport.ADMIN_SESSION_COOKIE
import com.example.template.testsupport.PostgresBackedIntegrationTest
import com.example.template.testsupport.registerAndLoginAdmin
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.web.context.WebApplicationContext
import kotlin.test.assertFalse
/**
 * Integration test: drives the real security filter chain (not controller-level checks)
 * over the existing Tournament API, so the guard is proven against a genuine endpoint rather
 * than a stub. The auth entry points stay reachable, everything else under `/api/`
 * demands a valid session cookie.
 */
@SpringBootTest(classes = [com.example.template.main.Application::class])
class ApiSessionGuardIntegrationTest(
    @Autowired private val context: WebApplicationContext,
    @Autowired private val objectMapper: ObjectMapper,
) : PostgresBackedIntegrationTest() {

    private val mockMvc: MockMvc = MockMvcBuilders.webAppContextSetup(context)
        .apply<org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder>(springSecurity())
        .build()

    private val frontendOrigin = "http://localhost:3000"

    @Test
    fun `unauthenticated request to a protected endpoint returns an RFC 7807 401 without data`() {
        val cookie = mockMvc.registerAndLoginAdmin(objectMapper)
        val marker = "secret-tournament-${java.util.UUID.randomUUID()}"
        mockMvc.perform(
            post("/api/tournaments")
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("name" to marker, "maxTeams" to 16))),
        ).andExpect(status().isCreated)

        val result = mockMvc.perform(get("/api/tournaments"))
            .andExpect(status().isUnauthorized)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
            .andReturn()

        // The error body is a stable problem document; no resource data leaks with it.
        assertFalse(
            result.response.contentAsString.contains(marker),
            "tournament data must not leak into the 401 body",
        )
    }

    @Test
    fun `register and login stay reachable without a session`() {
        val email = "guard-${java.util.UUID.randomUUID()}@example.com"

        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"s3cret-pw"}"""),
        ).andExpect(status().isCreated)

        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"s3cret-pw"}"""),
        ).andExpect(status().isOk)
    }

    @Test
    fun `a valid session cookie grants access to a previously public endpoint`() {
        val cookie = mockMvc.registerAndLoginAdmin(objectMapper)

        mockMvc.perform(get("/api/tournaments").cookie(cookie))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
    }

    @Test
    fun `an unknown session cookie does not grant access`() {
        mockMvc.perform(get("/api/tournaments").cookie(Cookie(ADMIN_SESSION_COOKIE, "not-a-real-token")))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
    }

    @Test
    fun `CORS allows only the configured frontend origin`() {
        mockMvc.perform(
            options("/api/tournaments")
                .header(HttpHeaders.ORIGIN, frontendOrigin)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type"),
        )
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, frontendOrigin))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "Content-Type"))
    }

    @Test
    fun `CORS rejects a foreign origin`() {
        mockMvc.perform(
            options("/api/tournaments")
                .header(HttpHeaders.ORIGIN, "http://evil.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"),
        ).andExpect(status().isForbidden)
    }
}
