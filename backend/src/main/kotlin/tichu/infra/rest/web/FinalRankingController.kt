package tichu.infra.rest.web

import tichu.application.api.AuthenticatedTeam
import tichu.application.api.GetFinalRanking
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Team-session read adapter for the Final Ranking (S7c, #116). Thin: it maps HTTP onto
 * [GetFinalRanking] and passes the authenticated team as the principal, which the
 * team-session filter chain guarantees. The request never names a tournament.
 */
@RestController
@RequestMapping("/api/team")
class FinalRankingController(
    private val getFinalRanking: GetFinalRanking,
) {

    /** The Final Ranking of the team's tournament; answers RESULTS_NOT_PUBLISHED until the organizer publishes. */
    @GetMapping("/me/final-ranking")
    fun finalRanking(
        @AuthenticationPrincipal principal: AuthenticatedTeam,
    ): List<StandingsEntryResponse> =
        getFinalRanking.get(principal).entries.map(StandingsEntryResponse::from)
}
