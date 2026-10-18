package com.example.template.infra.security

import com.example.template.domain.PasswordHasher
import com.example.template.domain.SessionTokens
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Wires the cryptography adapters behind the domain ports. Declared here rather than
 * discovered, in keeping with the template's explicit-@Bean rule.
 */
@Configuration
class SecurityConfiguration {

    @Bean
    fun passwordHasher(): PasswordHasher = BcryptPasswordHasher()

    @Bean
    fun sessionTokens(): SessionTokens = SecureSessionTokens()
}
