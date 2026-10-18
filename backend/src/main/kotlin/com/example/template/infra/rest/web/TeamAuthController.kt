package com.example.template.infra.rest.web

import com.example.template.application.api.ExchangeTeamAccess
import com.example.template.application.api.GetOwnTeam
import com.example.template.application.api.AuthenticatedTeam
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpHeaders
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Driving adapter for team authentication (Phase 5). The raw access token arrives
 * in the body and is exchanged for an httpOnly team-session cookie; the raw token
 * never appears in a response body. `/me` is the team-session-scoped read of the
 * team's own data — the principal comes from the session, never from the request.
 */
@RestController
@RequestMapping("/api/team")
class TeamAuthController(
    private val exchangeTeamAccess: ExchangeTeamAccess,
    private val getOwnTeam: GetOwnTeam,
    private val sessionCookie: SessionCookieFactory,
) {

    @PostMapping("/auth/exchange")
    fun exchange(
        @Valid @RequestBody body: TeamExchangeRequestDto,
        response: HttpServletResponse,
    ): TeamSessionResponseDto {
        val issued = exchangeTeamAccess.exchange(body.token)
        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie.session(issued.token).toString())
        return TeamSessionResponseDto(
            teamId = issued.teamId.value,
            tournamentId = issued.tournamentId.value,
            expiresAt = issued.expiresAt.toString(),
        )
    }

    @GetMapping("/me")
    fun me(@AuthenticationPrincipal principal: AuthenticatedTeam) =
        TeamResponseDto.from(getOwnTeam.get(principal))
}

data class TeamExchangeRequestDto(
    @field:NotBlank(message = "must not be blank")
    val token: String,
)

data class TeamSessionResponseDto(
    val teamId: String,
    val tournamentId: String,
    val expiresAt: String,
)