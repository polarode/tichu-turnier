package com.example.template.infra.rest.web

import com.example.template.application.api.AuthenticatedAdmin
import com.example.template.application.api.CreateTournament
import com.example.template.application.api.GetTournament
import com.example.template.application.api.ListTournaments
import com.example.template.domain.TournamentId
import com.example.template.domain.TournamentSettings
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * Driving adapter. Depends only on the inbound-port interfaces, which are injected
 * by [com.example.template.infra.rest.RestConfiguration]. No @Autowired; this is a
 * plain class constructed as a bean.
 *
 * Every method takes the authenticated admin from the security context and passes
 * that id to the use case; the owner is never read from the request, so a caller
 * cannot claim another admin's data.
 */
@RestController
@RequestMapping("/api/tournaments")
class TournamentController(
    private val createTournament: CreateTournament,
    private val listTournaments: ListTournaments,
    private val getTournament: GetTournament,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid @RequestBody body: CreateTournamentRequestBody,
        @AuthenticationPrincipal principal: AuthenticatedAdmin,
    ): TournamentResponse {
        val tournament = createTournament.create(
            CreateTournament.CreateTournamentRequest(
                name = body.name,
                description = body.description,
                maxTeams = body.maxTeams,
                settings = TournamentSettings(allowGrandTichu = body.settings.allowGrandTichu),
            ),
            principal.adminUserId,
        )
        return TournamentResponse.from(tournament)
    }

    @GetMapping
    fun list(@AuthenticationPrincipal principal: AuthenticatedAdmin): List<TournamentResponse> =
        listTournaments.list(principal.adminUserId).map(TournamentResponse::from)

    @GetMapping("/{id}")
    fun get(
        @PathVariable id: String,
        @AuthenticationPrincipal principal: AuthenticatedAdmin,
    ): TournamentResponse = TournamentResponse.from(
        getTournament.get(TournamentId(id), principal.adminUserId),
    )
}
