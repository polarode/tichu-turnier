package com.example.template.infra.persistence

import com.example.template.domain.IdGenerator
import com.example.template.domain.TaskRepository
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

/**
 * Wiring for the persistence adapter. The repository port is implemented by the
 * JPA adapter on PostgreSQL; the schema is created and migrated by Flyway.
 *
 * `@EnableJpaRepositories` / `@EntityScan` point Spring Data at this package —
 * `Application` does no classpath scanning, so discovery must be explicit here.
 */
@Configuration
@EnableJpaRepositories(basePackages = ["com.example.template.infra.persistence"])
@EntityScan(basePackages = ["com.example.template.infra.persistence"])
class PersistenceConfiguration(
    private val springDataTaskRepository: SpringDataTaskRepository,
) {

    @Bean
    fun taskRepository(): TaskRepository = JpaTaskRepository(springDataTaskRepository)

    @Bean
    fun idGenerator(): IdGenerator = UuidIdGenerator()
}
