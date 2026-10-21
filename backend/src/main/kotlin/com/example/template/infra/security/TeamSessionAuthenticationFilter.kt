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
 * authorization decision runs, but only for the team's own endpoint subtree:
 * a request outside `/api/team/` is never touched, so an established admin
 * authentication survives.
 *
 * Within `/api/team/`, a present team cookie wins over an already-established
 * authentication: one browser is routinely both organizer and team member, and
 * the admin filter runs first. The admin principal alone (no team cookie) is left
 * in place, so the authorization rules reject it with 403 as designed.
 */
class TeamSessionAuthenticationFilter(
    private val sessionCookie: SessionCookieFactory,
    private val authenticateTeamSession: AuthenticateTeamSession,
) : OncePerRequestFilter() {

    private companion object {
        /** The team principal is only ever consulted for the team's own subtree. */
        private const val TEAM_ENDPOINT_PREFIX = "/api/team/"
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val uri = request.requestURI ?: return true
        return !uri.startsWith(TEAM_ENDPOINT_PREFIX)
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
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
                // An invalid team cookie cannot downgrade an otherwise established
                // authentication; the authorization rules still apply.
            }
        }
        filterChain.doFilter(request, response)
    }
}
