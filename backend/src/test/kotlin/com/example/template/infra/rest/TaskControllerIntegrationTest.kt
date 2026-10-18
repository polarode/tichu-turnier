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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

/**
 * Integration test: boots the real application context (all beans wired through the
 * `*Configuration` classes) against Testcontainers PostgreSQL and drives the
 * controller over MockMvc through the real security chain. `@Autowired` is fine in
 * tests — the no-field-injection rule applies to production code.
 */
@SpringBootTest(classes = [com.example.template.main.Application::class])
class TaskControllerIntegrationTest(
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
    fun `create then fetch a task`() {
        val id = createTask("Write docs", "for the template")

        mockMvc.perform(get("/api/tasks/$id").cookie(sessionCookie))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.title").value("Write docs"))
            .andExpect(jsonPath("$.status").value("TODO"))
    }

    @Test
    fun `list returns created tasks`() {
        createTask("A", "")
        createTask("B", "")

        mockMvc.perform(get("/api/tasks").cookie(sessionCookie))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.length()").value(2))
    }

    @Test
    fun `create rejects a blank title with 400`() {
        mockMvc.perform(
            post("/api/tasks")
                .cookie(sessionCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"title":"","description":"x"}"""),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `update changes status`() {
        val id = createTask("A", "a")

        mockMvc.perform(
            put("/api/tasks/$id")
                .cookie(sessionCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"title":"A","description":"a","status":"DONE"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("DONE"))
    }

    @Test
    fun `delete removes the task`() {
        val id = createTask("A", "")

        mockMvc.perform(delete("/api/tasks/$id").cookie(sessionCookie))
            .andExpect(status().isNoContent)
        mockMvc.perform(get("/api/tasks/$id").cookie(sessionCookie))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `fetching an unknown task returns 404`() {
        mockMvc.perform(get("/api/tasks/does-not-exist").cookie(sessionCookie))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `a foreign admin cannot read, modify or delete another admin's task`() {
        val id = createTask("Alice's task", "private")

        mockMvc.perform(get("/api/tasks/$id").cookie(otherAdminCookie))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))

        mockMvc.perform(
            put("/api/tasks/$id")
                .cookie(otherAdminCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"title":"hijacked","description":"","status":"DONE"}"""),
        ).andExpect(status().isNotFound)

        mockMvc.perform(delete("/api/tasks/$id").cookie(otherAdminCookie))
            .andExpect(status().isNotFound)

        // The owner's task is untouched throughout.
        mockMvc.perform(get("/api/tasks/$id").cookie(sessionCookie))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.title").value("Alice's task"))
    }

    @Test
    fun `list returns only the caller's own tasks`() {
        createTask("alice-only", "")
        createTaskAs(otherAdminCookie, "bob-only", "")

        mockMvc.perform(get("/api/tasks").cookie(otherAdminCookie))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("bob-only")))
            .andExpect(content().string(not(containsString("alice-only"))))
    }

    private fun createTask(title: String, description: String): String =
        createTaskAs(sessionCookie, title, description)

    private fun createTaskAs(cookie: Cookie, title: String, description: String): String {
        val body = mapOf("title" to title, "description" to description)
        val response = mockMvc.perform(
            post("/api/tasks")
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
