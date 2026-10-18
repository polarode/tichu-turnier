package com.example.template.infra.security

import com.example.template.application.api.AuthenticateSession
import com.example.template.domain.PasswordHasher
import com.example.template.domain.SessionTokens
import com.example.template.infra.rest.web.SessionCookieFactory
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.DispatcherType
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

/**
 * Wires the cryptography adapters behind the domain ports and the API-wide security
 * filter chain. Declared here rather than discovered, in keeping with the template's
 * explicit-@Bean rule.
 *
 * The chain is stateless (the session lives in an httpOnly cookie, not an HttpSession),
 * has no CSRF token (a cookie + restricted CORS + JSON-only API makes CSRF ineffective;
 * see the plan) and delegates authentication to [AdminSessionAuthenticationFilter].
 */
@Configuration
class SecurityConfiguration(
    @param:Value("\${app.frontend-url}") private val frontendUrl: String,
) {

    @Bean
    fun passwordHasher(): PasswordHasher = BcryptPasswordHasher()

    @Bean
    fun sessionTokens(): SessionTokens = SecureSessionTokens()

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration().apply {
            allowedOrigins = listOf(frontendUrl)
            allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
            // JSON bodies carry a non-simple Content-Type, so preflight must allow it.
            allowedHeaders = listOf("Content-Type")
            allowCredentials = true
        }
        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/api/**", configuration)
        }
    }

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        sessionCookie: SessionCookieFactory,
        authenticateSession: AuthenticateSession,
        corsConfigurationSource: CorsConfigurationSource,
        objectMapper: ObjectMapper,
    ): SecurityFilterChain {
        val authenticationFilter = AdminSessionAuthenticationFilter(sessionCookie, authenticateSession)

        return http
            // Stateless cookie auth: no server-side HttpSession, no default logout handler.
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .logout { it.disable() }
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource) }
            .authorizeHttpRequests { authorize ->
                // Otherwise a failed request re-enters the chain and turns into a 401.
                authorize.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                // Credential entry points only; refresh/logout validate their own cookie
                // and everything else under /api/** requires a valid session.
                authorize.requestMatchers(
                    HttpMethod.POST,
                    "/api/auth/register",
                    "/api/auth/login",
                ).permitAll()
                // Deny by default: the API lives under /api/**, and any future
                // endpoint is protected unless it is deliberately opened above.
                authorize.anyRequest().authenticated()
            }
            .exceptionHandling { exceptions ->
                exceptions.authenticationEntryPoint(ProblemDetailAuthenticationEntryPoint(objectMapper))
                exceptions.accessDeniedHandler(ProblemDetailAccessDeniedHandler(objectMapper))
            }
            // Must run before the anonymous filter, otherwise an anonymous token is
            // already in the context and the cookie would never be considered.
            .addFilterBefore(authenticationFilter, AnonymousAuthenticationFilter::class.java)
            .build()
    }
}
