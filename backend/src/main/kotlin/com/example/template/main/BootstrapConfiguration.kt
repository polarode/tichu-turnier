package com.example.template.main

import com.example.template.application.api.BootstrapOwner
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Runs the one-off owner provisioning after the context is ready, so Flyway has already
 * migrated the schema. Declared as an explicit bean like every other piece of wiring;
 * the use case itself stays framework-free.
 */
@Configuration
class BootstrapConfiguration {

    @Bean
    fun ownerBootstrapRunner(bootstrapOwner: BootstrapOwner): ApplicationRunner =
        ApplicationRunner { bootstrapOwner.ensureOwner() }
}
