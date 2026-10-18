package com.example.template.infra.rest

import com.example.template.domain.AdminRole
import com.example.template.domain.AdminStatus
import com.example.template.domain.AdminUserRepository
import com.example.template.domain.PasswordHasher
import com.example.template.testsupport.PostgresBackedIntegrationTest
import com.example.template.testsupport.seedAdmin
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Integration test: boots the real application context (all beans wired through the
 * `*Configuration` classes) against Testcontainers PostgreSQL and drives the auth
 * endpoints over MockMvc. Verifies the HTTP contract: status codes, stable error
 * codes, and the session cookie shape — the raw token must never leak into a body.
 */
@SpringBootTest(classes = [com.example.template.main.Application::class])
class AuthControllerIntegrationTest(
    @Autowired private val context: WebApplicationContext,
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

    private fun setCookie(result: MvcResult): String =
        result.response.getHeader(HttpHeaders.SET_COOKIE) ?: error("no Set-Cookie header on response")

    private fun tokenFrom(result: MvcResult): String =
        setCookie(result).substringAfter("admin_session=").substringBefore(';')

    private fun withSessionCookie(token: String) = Cookie("admin_session", token)

    @Test
    fun `register creates an account and never echoes the password`() {
        val email = uniqueEmail()

        register(email)
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.email").value(email))
            .andExpect(jsonPath("$.id").isNotEmpty)
            .andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.passwordHash").doesNotExist())
    }

    @Test
    fun `register rejects a duplicate email with a conflict code`() {
        val email = uniqueEmail()
        register(email).andExpect(status().isCreated)

        register(email)
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"))
    }

    @Test
    fun `login sets an httpOnly secure same-site cookie and returns no token in the body`() {
        val email = uniqueEmail()
        seedAdmin(adminUsers, passwordHasher, email)

        val result = login(email).andExpect(status().isOk).andReturn()

        val cookie = setCookie(result)
        assertTrue(cookie.contains("HttpOnly"), "cookie is httpOnly: $cookie")
        assertTrue(cookie.contains("Secure"), "cookie is Secure: $cookie")
        assertTrue(cookie.contains("SameSite=Lax"), "cookie is SameSite=Lax: $cookie")

        val token = tokenFrom(result)
        assertTrue(token.isNotBlank(), "cookie carries a token")
        assertFalse(
            result.response.contentAsString.contains(token),
            "the raw token must not appear in the response body",
        )
    }

    @Test
    fun `login with a wrong password returns 401 with a stable error code`() {
        val email = uniqueEmail()
        seedAdmin(adminUsers, passwordHasher, email)

        login(email, password = "wrong-password")
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
    }

    @Test
    fun `register creates a pending account that cannot log in`() {
        val email = uniqueEmail()
        register(email).andExpect(status().isCreated)

        val result = login(email)
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("ACCOUNT_PENDING"))
            .andReturn()

        assertTrue(
            result.response.getHeaders(HttpHeaders.SET_COOKIE).isEmpty(),
            "a pending account must not be issued a session cookie",
        )
    }

    @Test
    fun `login with a wrong password on a pending account stays invalid credentials`() {
        val email = uniqueEmail()
        register(email)

        login(email, password = "wrong-password")
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
    }

    @Test
    fun `login on a disabled account is refused with a disabled code`() {
        val email = uniqueEmail()
        seedAdmin(adminUsers, passwordHasher, email)
        adminUsers.save(adminUsers.findByEmail(email)!!.copy(status = AdminStatus.DISABLED))

        login(email)
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"))
    }

    @Test
    fun `login with an unknown email returns 401 without revealing the account is absent`() {
        login(uniqueEmail())
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
    }

    @Test
    fun `refresh rotates the token and rejects the old one`() {
        val email = uniqueEmail()
        seedAdmin(adminUsers, passwordHasher, email)
        val first = tokenFrom(login(email).andReturn())

        val refreshed = mockMvc.perform(
            post("/api/auth/refresh").cookie(withSessionCookie(first)),
        ).andExpect(status().isOk).andReturn()

        val second = tokenFrom(refreshed)
        assertNotEquals(first, second, "refresh issues a new token")

        mockMvc.perform(post("/api/auth/refresh").cookie(withSessionCookie(first)))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `the session view carries the account role`() {
        val email = uniqueEmail()
        seedAdmin(adminUsers, passwordHasher, email, role = AdminRole.OWNER)

        val loginResult = login(email)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.role").value("OWNER"))
            .andReturn()

        mockMvc.perform(post("/api/auth/refresh").cookie(withSessionCookie(tokenFrom(loginResult))))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.role").value("OWNER"))
    }

    @Test
    fun `registration reports the non-owner role it created`() {
        register(uniqueEmail())
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.role").value("ADMIN"))
    }

    @Test
    fun `refresh without a cookie returns 401 with an unauthenticated code`() {
        mockMvc.perform(post("/api/auth/refresh"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
    }

    @Test
    fun `logout clears the cookie and invalidates the session`() {
        val email = uniqueEmail()
        seedAdmin(adminUsers, passwordHasher, email)
        val token = tokenFrom(login(email).andReturn())

        val logoutResult = mockMvc.perform(
            post("/api/auth/logout").cookie(withSessionCookie(token)),
        ).andExpect(status().isNoContent).andReturn()

        val cleared = setCookie(logoutResult)
        assertTrue(cleared.contains("Max-Age=0"), "logout expires the cookie: $cleared")

        mockMvc.perform(post("/api/auth/refresh").cookie(withSessionCookie(token)))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `register rejects a too-short password with invalid input`() {
        register(uniqueEmail(), password = "short")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
    }
}
