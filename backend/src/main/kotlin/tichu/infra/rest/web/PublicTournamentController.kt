package tichu.infra.rest.web

import tichu.application.api.GetPublicStatistics
import tichu.application.api.GetPublicTournament
import tichu.application.api.ListPublicTournaments
import tichu.domain.TournamentId
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Driving adapter for the anonymous public read surface (ADR 0008). Served under
 * the public prefix, which the security chain opens for GET only. Takes no
 * `@AuthenticationPrincipal`: visibility is decided by the use cases alone, never by
 * whoever happens to be calling.
 */
@RestController
@RequestMapping("/api/public/tournaments")
class PublicTournamentController(
    private val listPublicTournaments: ListPublicTournaments,
    private val getPublicTournament: GetPublicTournament,
    private val getPublicStatistics: GetPublicStatistics,
) {

    @GetMapping
    fun list(): List<PublicTournamentResponse> =
        listPublicTournaments.list().map(PublicTournamentResponse::from)

    @GetMapping("/{id}")
    fun get(@PathVariable id: String): PublicTournamentDetailResponse =
        PublicTournamentDetailResponse.from(getPublicTournament.get(TournamentId(id)))

    @GetMapping("/{id}/statistics")
    fun statistics(@PathVariable id: String): TournamentStatisticsResponse =
        TournamentStatisticsResponse.from(getPublicStatistics.get(TournamentId(id)))
}
