package com.example.template.testsupport

import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.containers.PostgreSQLContainer

/**
 * Base class for integration tests that need the real persistence stack: boots a
 * single Testcontainers PostgreSQL instance for the whole test JVM and wires the
 * application `DataSource` to it via `@ServiceConnection`.
 *
 * Singleton-container pattern: the container is started once (on first class load)
 * and deliberately NOT managed by the `@Testcontainers` extension — the extension
 * would stop it after each test class while the cached Spring context still points
 * at its port. It is reaped automatically when the JVM exits. Subclasses must set
 * `classes = [Application::class]` on their `@SpringBootTest` annotation.
 */
abstract class PostgresBackedIntegrationTest {

    companion object {
        @ServiceConnection
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:17-alpine")
            .apply { start() }
    }
}
