package com.example.template.infra.rest

import com.example.template.application.api.BootstrapOwner
import com.example.template.domain.AdminRole
import com.example.template.domain.AdminUserRepository
import com.example.template.infra.security.BcryptPasswordHasher
import com.example.template.testsupport.PostgresBackedIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import kotlin.test.assertEquals

/**
 * Integration test with an owner configured: proves the startup runner provisions the
 * owner from configuration, that the credential it stores actually logs in, that a
 * re-run stays at exactly one owner, and that no request can mint an owner.
 */
@SpringBootTest(classes = [com.example.template.main.Application::class])
class OwnerBootstrapIntegrationTest(
    @Autowired private val context: WebApplicationContext,
    @Autowired private val bootstrapOwner: BootstrapOwner,
    @Autowired private val adminUsers: AdminUserRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : PostgresBackedIntegrationTest() {

    private val mockMvc: MockMvc = MockMvcBuilders.webAppContextSetup(context)
        .apply<org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder>(springSecurity())
        .build()

    @Test
    fun `the configured owner can log in`() {
        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$OWNER_EMAIL","password":"$OWNER_PASSWORD"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.email").value(OWNER_EMAIL))
    }

    @Test
    fun `a bootstrap re-run keeps exactly one owner`() {
        bootstrapOwner.ensureOwner()

        assertEquals(1, configuredOwnerCount(), "a re-run must not create a second owner for the configured email")
    }

    @Test
    fun `registration cannot mint an owner`() {
        val email = "would-be-owner-${java.util.UUID.randomUUID()}@example.com"

        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"s3cret-pw"}"""),
        ).andExpect(status().isCreated)

        assertEquals(AdminRole.ADMIN, adminUsers.findByEmail(email)!!.role)
    }

    /**
     * Scoped to the configured identity: the invariant the bootstrap owns is "one owner
     * for this email", while other tests may legitimately seed owner rows for their own
     * fixtures in the shared test database.
     */
    private fun configuredOwnerCount(): Int =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM admin_users WHERE role = 'OWNER' AND email = ?",
            Int::class.java,
            OWNER_EMAIL,
        )!!

    companion object {
        const val OWNER_EMAIL = "owner-bootstrap@example.com"
        const val OWNER_PASSWORD = "owner-s3cret-pw"

        // Computed once per JVM from the real hasher, then reused for every context
        // build, so a rebuild cannot invalidate the configured credential.
        private val OWNER_PASSWORD_HASH = BcryptPasswordHasher().hash(OWNER_PASSWORD)

        @JvmStatic
        @DynamicPropertySource
        fun ownerProperties(registry: DynamicPropertyRegistry) {
            registry.add("app.auth.owner.email") { OWNER_EMAIL }
            registry.add("app.auth.owner.password-hash") { OWNER_PASSWORD_HASH }
        }
    }
}
