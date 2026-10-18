package com.example.template.infra.security

import com.example.template.application.api.AuthenticateSession
import com.example.template.infra.rest.web.SessionCookieFactory
import com.example.template.vocabulary.UnauthenticatedException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Turns the admin session cookie into a Spring Security authentication before the
 * authorization decision runs. Runs for every request (cheap: only hashes and looks up
 * the cookie when one is present) and never rejects on its own — an absent or invalid
 * cookie simply leaves the request unauthenticated, so the filter chain's entry point
 * produces the RFC 7807 401. This is the server-side guard: it lives in the security
 * chain, not in any controller, so no endpoint can forget it.
 */
class AdminSessionAuthenticationFilter(
    private val sessionCookie: SessionCookieFactory,
    private val authenticateSession: AuthenticateSession,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        if (SecurityContextHolder.getContext().authentication == null) {
            sessionCookie.read(request)?.let { token ->
                try {
                    val admin = authenticateSession.authenticate(token)
                    val authentication = UsernamePasswordAuthenticationToken(
                        admin,
                        null,
                        listOf(SimpleGrantedAuthority(ADMIN_SESSION_AUTHORITY)),
                    )
                    SecurityContextHolder.getContext().authentication = authentication
                } catch (_: UnauthenticatedException) {
                    // Leave the request anonymous; the entry point answers with 401.
                    SecurityContextHolder.clearContext()
                }
            }
        }
        filterChain.doFilter(request, response)
    }
}
