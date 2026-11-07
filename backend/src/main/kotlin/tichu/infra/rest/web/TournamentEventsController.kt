package tichu.infra.rest.web

import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import tichu.application.api.AuthenticatedAdmin
import tichu.application.api.GetTournament
import tichu.domain.TournamentId
import tichu.infra.events.SseTournamentEventBroadcaster

/**
 * Driving adapter for the live-event stream (ADR 0007). Only the owning admin may
 * subscribe; ownership is checked through [GetTournament], so a foreign or unknown
 * tournament answers 404 before any stream opens.
 */
@RestController
@RequestMapping("/api/tournaments")
class TournamentEventsController(
    private val getTournament: GetTournament,
    private val broadcaster: SseTournamentEventBroadcaster,
) {

    @GetMapping("/{id}/events", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun events(
        @PathVariable id: String,
        @AuthenticationPrincipal principal: AuthenticatedAdmin,
        response: HttpServletResponse,
    ): SseEmitter {
        getTournament.get(TournamentId(id), principal.adminUserId)
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache")
        response.setHeader("X-Accel-Buffering", "no")
        return broadcaster.subscribe(TournamentId(id))
    }
}
