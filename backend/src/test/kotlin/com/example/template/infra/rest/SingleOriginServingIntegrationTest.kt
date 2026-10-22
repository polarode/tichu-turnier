package com.example.template.infra.rest

import com.example.template.domain.AdminUserRepository
import com.example.template.domain.PasswordHasher
import com.example.template.main.Application
import com.example.template.testsupport.PostgresBackedIntegrationTest
import com.example.template.testsupport.loginAsSeededAdmin
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

/**
 * Integration test: boots the real application context against Testcontainers PostgreSQL
 * and drives the single-origin serving contract (ADR 0002). It proves that the SPA
 * fallback serves `index.html` for unmatched non-API paths, that unknown API paths
 * still return a JSON ProblemDetail instead of HTML, that `/health` answers
 * unauthenticated with a DB-backed readiness check, and that static assets are
 * unprotected while the API namespace stays protected.
 */
@SpringBootTest(classes = [Application::class])
class SingleOriginServingIntegrationTest(
    @Autowired private val context: WebApplicationContext,
    @Autowired private val adminUsers: AdminUserRepository,
    @Autowired private val passwordHasher: PasswordHasher,
    @Autowired private val objectMapper: ObjectMapper,
) : PostgresBackedIntegrationTest() {

    private val mockMvc: MockMvc = MockMvcBuilders.webAppContextSetup(context)
        .apply<DefaultMockMvcBuilder>(springSecurity())
        .build()

    private fun expectedIndexHtml(): String =
        javaClass.getResourceAsStream("/static/index.html")!!.readBytes().decodeToString()

    // `/health` lives outside the API namespace, is unauthenticated, and reflects DB readiness.
    @Test
    fun `health answers 200 unauthenticated`() {
        mockMvc.perform(get("/health"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("UP"))
    }

    // An unmatched non-API path — e.g. a client-side route deep link — falls back to
    // the SPA entry point instead of a 404, so browser refreshes work on any route.
    @Test
    fun `unmatched non-api path falls back to index html`() {
        mockMvc.perform(get("/admin/some/deep/link"))
            .andExpect(status().isOk)
            .andExpect(content().string(expectedIndexHtml()))
    }

    // The API namespace never falls back to the SPA: an unknown API path must stay a
    // JSON ProblemDetail, never the SPA's index.html. The request authenticates so the
    // 404 comes from routing, not the security chain (an anonymous caller gets a 401
    // ProblemDetail from the chain instead — still JSON, still not HTML).
    @Test
    fun `unknown api path returns json problem detail`() {
        val session = mockMvc.loginAsSeededAdmin(adminUsers, passwordHasher, objectMapper)
        mockMvc.perform(get("/api/nonexistent").cookie(session))
            .andExpect(status().isNotFound)
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.title").exists())
    }

    // Static assets and the SPA root are anonymous; the API namespace is not.
    @Test
    fun `static assets are anonymous but api stays protected`() {
        mockMvc.perform(get("/")).andExpect(status().isOk)
        mockMvc.perform(get("/index.html")).andExpect(status().isOk)
        mockMvc.perform(get("/api/tournaments")).andExpect(status().isUnauthorized)
    }

    // A real asset is served with its own content by the resource handler — proof
    // that the SPA fallback router does not shadow the static asset mapping.
    @Test
    fun `real assets are served with their own content`() {
        val expected = javaClass.getResourceAsStream("/static/assets/placeholder.js")!!.readBytes().decodeToString()
        mockMvc.perform(get("/assets/placeholder.js"))
            .andExpect(status().isOk)
            .andExpect(content().string(expected))
    }
}
