package com.example.template.infra.security

import com.example.template.application.api.AuthenticateTeamSession
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
 * Turns the team session cookie into a Spring Security authentication before the
 * authorization decision runs. Mirrors [AdminSessionAuthenticationFilter], but for
 * the team principal: an absent or invalid team cookie leaves the request
 * unauthenticated so the filter chain's entry point produces the RFC 7807 401.
 */
class TeamSessionAuthenticationFilter(
    private val sessionCookie: SessionCookieFactory,
    private val authenticateTeamSession: AuthenticateTeamSession,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        if (SecurityContextHolder.getContext().authentication == null) {
            sessionCookie.read(request)?.let { token ->
                try {
                    val team = authenticateTeamSession.authenticate(token)
                    val authentication = UsernamePasswordAuthenticationToken(
                        team,
                        null,
                        listOf(SimpleGrantedAuthority(TEAM_SESSION_AUTHORITY)),
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