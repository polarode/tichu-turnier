package com.example.template.infra.config

import com.example.template.main.Application
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.env.Environment
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import com.example.template.testsupport.PostgresBackedIntegrationTest

/**
 * Boots the real application under the `prod` profile and asserts that it
 * comes up using only environment-style variables (no baked-in defaults for
 * the datasource, the Flyway connection, or the frontend origin), that Flyway
 * migrated the schema, that a missing variable fails fast, and that the
 * security posture matches production (forward-headers strategy, `Secure`
 * session cookie).
 *
 * The properties are deliberately named exactly like the environment variables
 * the prod profile's placeholders resolve (`SPRING_DATASOURCE_URL`, ...), so
 * the test exercises the same placeholder names a real deployment resolves.
 * (Strictly, real env vars arrive via relaxed-binding env source; the outcome
 * for these bare placeholders is identical.)
 */
@SpringBootTest(
    classes = [Application::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@ActiveProfiles("prod")
class ProdProfileIntegrationTest {

    @Autowired
    lateinit var env: Environment

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @Test
    fun `prod profile boots from env vars alone with flyway on the direct URL`() {
        // The context started: the pooled runtime datasource and the Flyway
        // direct-connection URL both resolved from the SPRING_* variables, and
        // ddl-auto: validate passed against the migrated schema. Assert on
        // Flyway's own history table, not just any table in `public`.
        val appliedMigrations = jdbcTemplate.queryForObject(
            "select count(*) from flyway_schema_history where success = true",
            Int::class.java,
        )
        assertThat(appliedMigrations).isGreaterThan(0)
    }

    @Test
    fun `forward headers strategy is framework so the TLS proxy's scheme wins`() {
        assertThat(env.getProperty("server.forward-headers-strategy")).isEqualTo("framework")
    }

    @Test
    fun `session-cookie-secure is set explicitly in the prod profile, not inherited`() {
        // The prod profile must own this value: if the base default ever
        // flipped, this assertion still pins prod to Secure.
        assertThat(env.getProperty("app.auth.session-cookie-secure")).isEqualTo("true")
    }

    @Test
    fun `session cookies stay Secure in prod`() {
        val response = restTemplate.exchange(
            "/api/auth/login",
            HttpMethod.POST,
            HttpEntity(
                """{"email":"$ownerEmail","password":${ownerPasswordJson}}""",
                HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON },
            ),
            String::class.java,
        )
        assertThat(response.statusCode.is2xxSuccessful).isTrue()
        val setCookie = response.headers.getFirst(HttpHeaders.SET_COOKIE)
            ?: error("no Set-Cookie header on login response")
        assertThat(setCookie).contains("Secure")
    }

    @Test
    fun `boot fails fast when SPRING_FLYWAY_URL is missing`() {
        val error = runCatching {
            prodBoot(
                "SPRING_DATASOURCE_URL", "SPRING_DATASOURCE_USERNAME", "SPRING_DATASOURCE_PASSWORD",
                "APP_FRONTEND_URL", "OWNER_EMAIL", "OWNER_PASSWORD_HASH",
            )
        }.exceptionOrNull()
        assertThat(error).isNotNull()
        assertThat(error?.causeChainMessages()).anyMatch { it.contains("SPRING_FLYWAY_URL") }
    }

    @Test
    fun `boot fails fast when APP_FRONTEND_URL is missing`() {
        val error = runCatching {
            prodBoot(
                "SPRING_DATASOURCE_URL", "SPRING_DATASOURCE_USERNAME", "SPRING_DATASOURCE_PASSWORD",
                "SPRING_FLYWAY_URL", "OWNER_EMAIL", "OWNER_PASSWORD_HASH",
            )
        }.exceptionOrNull()
        assertThat(error).isNotNull()
        assertThat(error?.causeChainMessages()).anyMatch { it.contains("APP_FRONTEND_URL") }
    }

    private fun Throwable.causeChainMessages(): List<String> =
        generateSequence(this) { it.cause }.take(10).map { it.message ?: "" }.toList()

    /**
     * Boots the app in non-web mode with the prod profile, setting only the
     * named env-style variables. Throws when the context cannot start.
     */
    private fun prodBoot(vararg variables: String) {
        val postgres = PostgresBackedIntegrationTest.postgres
        val values = mapOf(
            "SPRING_DATASOURCE_URL" to postgres.jdbcUrl,
            "SPRING_DATASOURCE_USERNAME" to postgres.username,
            "SPRING_DATASOURCE_PASSWORD" to postgres.password,
            "SPRING_FLYWAY_URL" to postgres.jdbcUrl,
            "APP_FRONTEND_URL" to "https://tichu.example.org",
            "OWNER_EMAIL" to ownerEmail,
            "OWNER_PASSWORD_HASH" to ownerPasswordHash,
        )
        // Only pass the variables the test names — a missing env var must be
        // truly absent, not present-but-empty, or the placeholder would resolve.
        val args = variables
            .map { "--${it}=${values[it]}" }
            .toTypedArray()
        val context = SpringApplicationBuilder(Application::class.java)
            .profiles("prod")
            // SERVLET, not NONE: the frontend URL is consumed by the security
            // and CORS beans, which only instantiate in a web context. Port 0
            // keeps the short-lived server off any real port.
            .web(WebApplicationType.SERVLET)
            .properties("server.port=0")
            .run(*args)
        context.close()
    }

    companion object {
        private const val ownerEmail = "owner@tichu.example.org"
        private const val ownerPassword = "s3cret-pw"

        private val ownerPasswordJson = run {
            val encoder = com.fasterxml.jackson.databind.ObjectMapper()
            encoder.writeValueAsString(ownerPassword)
        }

        // Computed once; resolved lazily by the dynamic property supplier.
        private val ownerPasswordHash: String by lazy {
            BCryptPasswordEncoder().encode(ownerPassword)
        }

        @JvmStatic
        @DynamicPropertySource
        fun prodEnvVars(registry: DynamicPropertyRegistry) {
            val postgres = PostgresBackedIntegrationTest.postgres
            registry.add("SPRING_DATASOURCE_URL") { postgres.jdbcUrl }
            registry.add("SPRING_DATASOURCE_USERNAME") { postgres.username }
            registry.add("SPRING_DATASOURCE_PASSWORD") { postgres.password }
            registry.add("SPRING_FLYWAY_URL") { postgres.jdbcUrl }
            registry.add("APP_FRONTEND_URL") { "https://tichu.example.org" }
            registry.add("OWNER_EMAIL") { ownerEmail }
            registry.add("OWNER_PASSWORD_HASH") { ownerPasswordHash }
        }
    }
}
