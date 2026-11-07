package tichu.infra.rest.web

import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import tichu.application.api.CheckPublicTournamentVisible
import tichu.domain.TournamentEventType
import tichu.domain.TournamentId
import tichu.infra.events.SseTournamentEventBroadcaster

/**
 * Driving adapter for the anonymous public live stream. Carries only the events a spectator
 * may see (round starts, tournament finish, results publication). A tournament that is not
 * publicly visible answers 404 through [CheckPublicTournamentVisible] before any stream opens.
 */
@RestController
@RequestMapping("/api/public/tournaments")
class PublicTournamentEventsController(
    private val checkPublicTournamentVisible: CheckPublicTournamentVisible,
    private val broadcaster: SseTournamentEventBroadcaster,
) {

    @GetMapping("/{id}/events", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun events(@PathVariable id: String, response: HttpServletResponse): SseEmitter {
        checkPublicTournamentVisible.check(TournamentId(id))
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache")
        response.setHeader("X-Accel-Buffering", "no")
        return broadcaster.subscribe(
            TournamentId(id),
            setOf(
                TournamentEventType.ROUND_STARTED,
                TournamentEventType.TOURNAMENT_FINISHED,
                TournamentEventType.RESULTS_PUBLISHED,
            ),
        )
    }
}
