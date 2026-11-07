package tichu.infra.rest.web

import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import tichu.application.api.AuthenticatedTeam
import tichu.infra.events.SseTournamentEventBroadcaster

/**
 * Driving adapter for the team's live-event stream (ADR 0007). The team stream carries
 * every event of the team's own tournament. The scope comes from the team session only:
 * the request names no tournament, so a team can never subscribe to another one.
 */
@RestController
@RequestMapping("/api/team")
class TeamEventsController(
    private val broadcaster: SseTournamentEventBroadcaster,
) {

    @GetMapping("/events", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun events(
        @AuthenticationPrincipal principal: AuthenticatedTeam,
        response: HttpServletResponse,
    ): SseEmitter {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache")
        response.setHeader("X-Accel-Buffering", "no")
        return broadcaster.subscribe(principal.tournamentId)
    }
}
