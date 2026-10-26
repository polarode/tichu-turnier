package com.example.template.infra.rest.web

import com.example.template.application.api.AuthenticatedAdmin
import com.example.template.application.api.CreateTournament
import com.example.template.application.api.DeleteTournament
import com.example.template.application.api.GetTournament
import com.example.template.application.api.ListTournaments
import com.example.template.application.api.StartTournament
import com.example.template.application.api.TournamentWriteRequest
import com.example.template.application.api.UpdateTournament
import com.example.template.domain.TournamentId
import com.example.template.domain.TournamentSettings
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
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
    private val updateTournament: UpdateTournament,
    private val deleteTournament: DeleteTournament,
    private val startTournament: StartTournament,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid @RequestBody body: TournamentWriteRequestBody,
        @AuthenticationPrincipal principal: AuthenticatedAdmin,
    ): TournamentResponse = TournamentResponse.from(
        createTournament.create(body.toWriteRequest(), principal.adminUserId),
    )

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

    @PostMapping("/{id}/start")
    fun start(
        @PathVariable id: String,
        @AuthenticationPrincipal principal: AuthenticatedAdmin,
    ): StartedTournamentResponse = StartedTournamentResponse.from(
        startTournament.start(TournamentId(id), principal.adminUserId),
    )

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: String,
        @Valid @RequestBody body: TournamentWriteRequestBody,
        @AuthenticationPrincipal principal: AuthenticatedAdmin,
    ): TournamentResponse = TournamentResponse.from(
        updateTournament.update(TournamentId(id), body.toWriteRequest(), principal.adminUserId),
    )

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable id: String,
        @AuthenticationPrincipal principal: AuthenticatedAdmin,
    ) {
        deleteTournament.delete(TournamentId(id), principal.adminUserId)
    }

    /** One wire-body -> application-payload mapping, shared by create and update. */
    private fun TournamentWriteRequestBody.toWriteRequest(): TournamentWriteRequest =
        TournamentWriteRequest(
            name = name,
            description = description,
            maxTeams = maxTeams,
            settings = TournamentSettings(allowGrandTichu = settings.allowGrandTichu),
        )
}
