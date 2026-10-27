package com.example.template.main

import com.example.template.application.usecase.UseCaseConfiguration
import com.example.template.infra.persistence.PersistenceConfiguration
import com.example.template.infra.rest.RestConfiguration
import com.example.template.infra.rest.SpaWebConfiguration
import com.example.template.infra.security.SecurityConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

/**
 * Composition root. Rather than relying on `@ComponentScan` to discover beans, we
 * explicitly `@Import` each configuration. This keeps wiring visible in one place
 * and enforces the rule that beans are declared in `*Configuration` classes.
 *
 * `@EnableAutoConfiguration` still gives us Spring Boot's web/Jackson/etc. setup.
 * The default in-memory `UserDetailsService` is excluded because authentication is
 * backed by our own session repository, not Spring Security's user store.
 */
@Configuration
@EnableAutoConfiguration(exclude = [UserDetailsServiceAutoConfiguration::class])
@Import(
    UseCaseConfiguration::class,
    PersistenceConfiguration::class,
    SecurityConfiguration::class,
    RestConfiguration::class,
    SpaWebConfiguration::class,
    BootstrapConfiguration::class,
)
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}
