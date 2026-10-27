package com.example.template.infra.rest

import com.example.template.domain.AdminInvite
import com.example.template.domain.AdminInviteRepository
import com.example.template.domain.AdminRole
import com.example.template.domain.AdminStatus
import com.example.template.domain.AdminUserRepository
import com.example.template.domain.InviteId
import com.example.template.domain.InviteStatus
import com.example.template.domain.PasswordHasher
import com.example.template.domain.SessionTokens
import com.example.template.domain.normalizeEmail
import com.example.template.testsupport.PostgresBackedIntegrationTest
import com.example.template.testsupport.loginAndCaptureSession
import com.example.template.testsupport.seedAdmin
import tools.jackson.databind.ObjectMapper
import jakarta.servlet.http.Cookie
import org.hamcrest.Matchers.hasItem
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
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Integration test: boots the real application context against Testcontainers PostgreSQL
 * and drives the owner invite endpoints plus invite-gated registration. It proves the
 * HTTP contract — the token is returned exactly once and never listed, an invite
 * activates its account immediately and is single-use, and each rejection carries a
 * stable code.
 */
@SpringBootTest(classes = [com.example.template.main.Application::class])
class InviteControllerIntegrationTest(
    @Autowired private val context: WebApplicationContext,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val adminUsers: AdminUserRepository,
    @Autowired private val adminInvites: AdminInviteRepository,
    @Autowired private val passwordHasher: PasswordHasher,
    @Autowired private val sessionTokens: SessionTokens,
) : PostgresBackedIntegrationTest() {

    private val mockMvc: MockMvc = MockMvcBuilders.webAppContextSetup(context)
        .apply<org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder>(springSecurity())
        .build()

    private fun uniqueEmail(prefix: String = "admin") = "$prefix-${UUID.randomUUID()}@example.com"

    private fun ownerCookie(): Cookie {
        val email = uniqueEmail("owner")
        seedAdmin(adminUsers, passwordHasher, email, role = AdminRole.OWNER)
        return mockMvc.loginAndCaptureSession(objectMapper, email)
    }

    /** Issues an invite through the API and returns the raw token the owner is shown once. */
    private fun issue(cookie: Cookie, email: String): String {
        val result = mockMvc.perform(
            post("/api/invites")
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email"}"""),
        ).andExpect(status().isCreated).andReturn()
        return objectMapper.readTree(result.response.contentAsString).get("token").asText()
    }

    private fun register(email: String, token: String?, password: String = "s3cret-pw") = mockMvc.perform(
        post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                buildString {
                    append("""{"email":"$email","password":"$password"""")
                    if (token != null) append(""","inviteToken":"$token"""")
                    append("}")
                },
            ),
    )

    private fun seedInvite(
        rawToken: String,
        email: String,
        status: InviteStatus = InviteStatus.PENDING,
        expiresAt: Instant = Instant.now().plus(Duration.ofDays(7)),
    ): AdminInvite = adminInvites.save(
        AdminInvite(
            id = InviteId(UUID.randomUUID().toString()),
            email = normalizeEmail(email),
            tokenHash = sessionTokens.hash(rawToken),
            status = status,
            createdBy = seedAdmin(adminUsers, passwordHasher, uniqueEmail("issuer"), role = AdminRole.OWNER).id,
            createdAt = Instant.now(),
            expiresAt = expiresAt,
            spentAt = if (status == InviteStatus.SPENT) Instant.now() else null,
        ),
    )

    @Test
    fun `issuing returns the token exactly once and no listing ever leaks it`() {
        val cookie = ownerCookie()
        val email = uniqueEmail("invitee")

        val result = mockMvc.perform(
            post("/api/invites")
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email"}"""),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.email").value(email))
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.token").isNotEmpty)
            .andExpect(jsonPath("$.link").isNotEmpty)
            .andReturn()

        val body = objectMapper.readTree(result.response.contentAsString)
        val token = body.get("token").asText()
        assertTrue(body.get("link").asText().contains(token), "the copyable link embeds the token")

        // The database holds only the digest.
        val stored = adminInvites.findByTokenHash(sessionTokens.hash(token))
        assertTrue(stored != null, "the invite is persisted under its token hash")
        assertNotEquals(token, stored!!.tokenHash, "the raw token must never be stored")

        // Neither the invite listing nor the admin listing reproduces the token.
        val invitesBody = mockMvc.perform(get("/api/invites").cookie(cookie))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[*].email", hasItem(email)))
            .andReturn().response.contentAsString
        assertFalse(invitesBody.contains(token), "the invite listing must never return the token")

        val adminsBody = mockMvc.perform(get("/api/admins").cookie(cookie))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        assertFalse(adminsBody.contains(token), "no other API may return the token")
    }

    @Test
    fun `registering with a valid invite for that email activates immediately and consumes it`() {
        val cookie = ownerCookie()
        val email = uniqueEmail("invitee")
        val token = issue(cookie, email)

        register(email, token)
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.password").doesNotExist())

        // The account can log in without an approval.
        mockMvc.loginAndCaptureSession(objectMapper, email)

        val account = adminUsers.findByEmail(email)!!
        assertEquals(AdminStatus.ACTIVE, account.status)
        assertTrue(account.invitedBy != null, "the inviter is recorded")
        assertTrue(account.invitedAt != null, "the invite time is recorded")

        // The owner console exposes the audit but no token.
        mockMvc.perform(get("/api/admins").param("status", "ACTIVE").cookie(cookie))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.email=='$email')].invitedBy").isNotEmpty)
            .andExpect(jsonPath("$[?(@.email=='$email')].invitedAt").isNotEmpty)

        val invite = adminInvites.findByTokenHash(sessionTokens.hash(token))!!
        assertEquals(InviteStatus.SPENT, invite.status, "the invite is consumed")
    }

    @Test
    fun `a spent invite cannot be used a second time`() {
        val cookie = ownerCookie()
        val email = uniqueEmail("invitee")
        val token = issue(cookie, email)
        register(email, token).andExpect(status().isCreated)

        register(uniqueEmail("other"), token)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVITE_INVALID"))
    }

    @Test
    fun `an expired invite is rejected with a stable code`() {
        val email = uniqueEmail("invitee")
        seedInvite("expired-token", email, expiresAt = Instant.now().minus(Duration.ofMinutes(1)))

        register(email, "expired-token")
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("INVITE_EXPIRED"))
    }

    @Test
    fun `a revoked invite is rejected with a stable code`() {
        val cookie = ownerCookie()
        val email = uniqueEmail("invitee")
        val token = issue(cookie, email)

        val inviteId = adminInvites.findByTokenHash(sessionTokens.hash(token))!!.id.value
        mockMvc.perform(post("/api/invites/$inviteId/revoke").cookie(cookie)).andExpect(status().isOk)

        register(email, token)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVITE_INVALID"))
    }

    @Test
    fun `an invite for a different email is rejected with a stable code`() {
        val cookie = ownerCookie()
        val token = issue(cookie, uniqueEmail("invited"))

        register(uniqueEmail("someone-else"), token)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVITE_EMAIL_MISMATCH"))
    }

    @Test
    fun `an unknown invite token is rejected with a stable code`() {
        register(uniqueEmail("invitee"), "never-issued")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVITE_INVALID"))
    }

    @Test
    fun `issuing an invite for an already registered email is refused`() {
        val cookie = ownerCookie()
        val email = uniqueEmail("taken")
        seedAdmin(adminUsers, passwordHasher, email)

        mockMvc.perform(
            post("/api/invites")
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email"}"""),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"))
    }

    @Test
    fun `a non-owner admin cannot issue or list invites`() {
        val email = uniqueEmail("admin")
        seedAdmin(adminUsers, passwordHasher, email)
        val cookie = mockMvc.loginAndCaptureSession(objectMapper, email)

        mockMvc.perform(
            post("/api/invites")
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"${uniqueEmail("invitee")}"}"""),
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("FORBIDDEN"))

        mockMvc.perform(get("/api/invites").cookie(cookie))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("FORBIDDEN"))
    }

    @Test
    fun `an unauthenticated caller gets 401, distinct from the 403`() {
        mockMvc.perform(get("/api/invites"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))

        mockMvc.perform(
            post("/api/invites")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"${uniqueEmail("invitee")}"}"""),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
    }

    @Test
    fun `revoking an unknown invite answers not found`() {
        mockMvc.perform(post("/api/invites/does-not-exist/revoke").cookie(ownerCookie()))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
    }
}
