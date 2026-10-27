package com.example.template.infra.rest

import com.example.template.domain.AdminUserRepository
import com.example.template.domain.PasswordHasher
import com.example.template.main.Application
import com.example.template.testsupport.PostgresBackedIntegrationTest
import com.example.template.testsupport.loginAsSeededAdmin
import tools.jackson.databind.ObjectMapper
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
 * Drives the single-origin serving contract (ADR 0002) against Testcontainers
 * PostgreSQL: SPA fallback, JSON 404 on unknown API paths, /health, security.
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

    @Test
    fun `health answers 200 unauthenticated`() {
        mockMvc.perform(get("/health"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("UP"))
    }

    @Test
    fun `unmatched non-api path falls back to index html`() {
        mockMvc.perform(get("/admin/some/deep/link"))
            .andExpect(status().isOk)
            .andExpect(content().string(expectedIndexHtml()))
    }

    // Authenticated so the 404 comes from routing, not the security chain
    // (an anonymous caller would get the chain's 401 ProblemDetail instead).
    @Test
    fun `unknown api path returns json problem detail`() {
        val session = mockMvc.loginAsSeededAdmin(adminUsers, passwordHasher, objectMapper)
        mockMvc.perform(get("/api/nonexistent").cookie(session))
            .andExpect(status().isNotFound)
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.title").exists())
    }

    @Test
    fun `static assets are anonymous but api stays protected`() {
        mockMvc.perform(get("/")).andExpect(status().isOk)
        mockMvc.perform(get("/index.html")).andExpect(status().isOk)
        mockMvc.perform(get("/api/tournaments")).andExpect(status().isUnauthorized)
    }

    // Own content proves the fallback does not shadow the resource handler.
    @Test
    fun `real assets are served with their own content`() {
        val expected = javaClass.getResourceAsStream("/static/assets/placeholder.js")!!.readBytes().decodeToString()
        mockMvc.perform(get("/assets/placeholder.js"))
            .andExpect(status().isOk)
            .andExpect(content().string(expected))
    }
}
