package tichu.infra.rest.web

import tichu.application.api.ListPublicTournaments
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Driving adapter for the anonymous public read surface (ADR 0008). Served under
 * the public prefix, which the security chain opens for GET only. Takes no
 * `@AuthenticationPrincipal`: visibility is decided by the use case alone, never by
 * whoever happens to be calling.
 */
@RestController
@RequestMapping("/api/public/tournaments")
class PublicTournamentController(
    private val listPublicTournaments: ListPublicTournaments,
) {

    @GetMapping
    fun list(): List<PublicTournamentResponse> =
        listPublicTournaments.list().map(PublicTournamentResponse::from)
}
