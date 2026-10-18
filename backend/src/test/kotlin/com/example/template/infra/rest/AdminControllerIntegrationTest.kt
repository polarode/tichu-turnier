package com.example.template.infra.rest

import com.example.template.domain.AdminRole
import com.example.template.domain.AdminUser
import com.example.template.domain.AdminUserRepository
import com.example.template.domain.PasswordHasher
import com.example.template.testsupport.PostgresBackedIntegrationTest
import com.example.template.testsupport.loginAndCaptureSession
import com.example.template.testsupport.seedAdmin
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.Cookie
import org.hamcrest.Matchers.hasItem
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Integration test: boots the real application context against Testcontainers PostgreSQL
 * and drives the owner admin-management endpoints. It proves the HTTP contract — role
 * enforcement (403 for a non-owner, distinct from the chain's 401), the pending-approval
 * lifecycle, live-session revocation on disable, and that no response carries credential
 * material.
 */
@SpringBootTest(classes = [com.example.template.main.Application::class])
class AdminControllerIntegrationTest(
    @Autowired private val context: WebApplicationContext,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val adminUsers: AdminUserRepository,
    @Autowired private val passwordHasher: PasswordHasher,
) : PostgresBackedIntegrationTest() {

    private val mockMvc: MockMvc = MockMvcBuilders.webAppContextSetup(context)
        .apply<org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder>(springSecurity())
        .build()

    private fun uniqueEmail() = "admin-${UUID.randomUUID()}@example.com"

    private fun register(email: String, password: String = "s3cret-pw") = mockMvc.perform(
        post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"email":"$email","password":"$password"}"""),
    )

    private fun login(email: String, password: String = "s3cret-pw") = mockMvc.perform(
        post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"email":"$email","password":"$password"}"""),
    )

    /** Seeds and logs in an owner, returning the session cookie the console calls with. */
    private fun ownerCookie(): Cookie {
        val email = uniqueEmail()
        seedAdmin(adminUsers, passwordHasher, email, role = AdminRole.OWNER)
        return mockMvc.loginAndCaptureSession(objectMapper, email)
    }

    /** Registers a request and returns the persisted (PENDING) account. */
    private fun pendingAccount(email: String = uniqueEmail()): AdminUser {
        register(email).andExpect(status().isCreated)
        return adminUsers.findByEmail(email)!!
    }

    private fun action(cookie: Cookie, id: String, name: String) = mockMvc.perform(
        post("/api/admins/$id/$name").cookie(cookie),
    )

    @Test
    fun `owner lists admins by status and the response carries no credential material`() {
        val pending = pendingAccount()

        val result = mockMvc.perform(
            get("/api/admins").param("status", "PENDING").cookie(ownerCookie()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[*].email", hasItem(pending.email)))
            .andReturn()

        val body = result.response.contentAsString
        assertFalse(body.contains("passwordHash"), "no password hash field may be serialized")
        assertFalse(body.contains(pending.passwordHash), "the stored hash must never be returned")
        assertFalse(body.contains("token"), "no token material may be serialized")
    }

    @Test
    fun `approving a pending request activates it and makes the account log in immediately`() {
        val pending = pendingAccount()

        action(ownerCookie(), pending.id.value, "approve")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.approvedBy").isNotEmpty)
            .andExpect(jsonPath("$.approvedAt").isNotEmpty)
            .andExpect(jsonPath("$.passwordHash").doesNotExist())

        login(pending.email).andExpect(status().isOk)
    }

    @Test
    fun `rejecting a pending request removes the account and it can no longer log in`() {
        val pending = pendingAccount()

        action(ownerCookie(), pending.id.value, "reject").andExpect(status().isNoContent)

        assertTrue(adminUsers.findByEmail(pending.email) == null, "a rejected request is removed")
        login(pending.email)
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
    }

    @Test
    fun `disabling an admin refuses its live session on the next request and reactivating restores login`() {
        val ownerEmail = uniqueEmail()
        seedAdmin(adminUsers, passwordHasher, ownerEmail, role = AdminRole.OWNER)
        val owner = mockMvc.loginAndCaptureSession(objectMapper, ownerEmail)

        val targetEmail = uniqueEmail()
        val target = seedAdmin(adminUsers, passwordHasher, targetEmail)
        val targetSession = mockMvc.loginAndCaptureSession(objectMapper, targetEmail)

        // While active, the live session works.
        mockMvc.perform(get("/api/tournaments").cookie(targetSession)).andExpect(status().isOk)

        action(owner, target.id.value, "disable").andExpect(status().isOk)

        // The very next request with the still-valid cookie is refused.
        mockMvc.perform(get("/api/tournaments").cookie(targetSession))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
        login(targetEmail).andExpect(status().isForbidden)

        // Reactivation restores login.
        action(owner, target.id.value, "reactivate").andExpect(status().isOk)
        login(targetEmail).andExpect(status().isOk)
    }

    @Test
    fun `a non-owner admin gets 403 FORBIDDEN on an owner-only endpoint`() {
        val email = uniqueEmail()
        val admin = seedAdmin(adminUsers, passwordHasher, email)
        val cookie = mockMvc.loginAndCaptureSession(objectMapper, email)
        val pending = pendingAccount()

        mockMvc.perform(get("/api/admins").cookie(cookie))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("FORBIDDEN"))

        action(cookie, pending.id.value, "approve")
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("FORBIDDEN"))

        // The refused call left the account pending.
        assertTrue(adminUsers.findById(admin.id)!!.isActive())
    }

    @Test
    fun `an unauthenticated caller gets 401 UNAUTHENTICATED, distinct from the 403`() {
        mockMvc.perform(get("/api/admins"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
    }

    @Test
    fun `mutating an unknown account answers not found`() {
        action(ownerCookie(), "does-not-exist", "approve")
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
    }

    @Test
    fun `a non-owner gets 403 even when the status filter is malformed`() {
        val email = uniqueEmail()
        seedAdmin(adminUsers, passwordHasher, email)
        val cookie = mockMvc.loginAndCaptureSession(objectMapper, email)

        mockMvc.perform(get("/api/admins").param("status", "NOPE").cookie(cookie))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("FORBIDDEN"))
    }

    @Test
    fun `an unknown status filter is a field-anchored invalid input`() {
        mockMvc.perform(get("/api/admins").param("status", "NOPE").cookie(ownerCookie()))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
            .andExpect(jsonPath("$.field").value("status"))
    }

    @Test
    fun `the owner account itself cannot be disabled`() {
        val ownerEmail = uniqueEmail()
        val owner = seedAdmin(adminUsers, passwordHasher, ownerEmail, role = AdminRole.OWNER)
        val cookie = mockMvc.loginAndCaptureSession(objectMapper, ownerEmail)

        action(cookie, owner.id.value, "disable")
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("FORBIDDEN"))
    }
}
