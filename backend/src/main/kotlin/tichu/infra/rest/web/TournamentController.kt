package tichu.infra.rest.web

import tichu.application.api.AuthenticatedAdmin
import tichu.application.api.CreateTournament
import tichu.application.api.DeleteTournament
import tichu.application.api.GetTournament
import tichu.application.api.ListTournaments
import tichu.application.api.ListRounds
import tichu.application.api.StartTournament
import tichu.application.api.TournamentWriteRequest
import tichu.application.api.UpdateTournament
import tichu.domain.TournamentId
import tichu.domain.TournamentSettings
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
 * by [tichu.infra.rest.RestConfiguration]. No @Autowired; this is a
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
    private val listRounds: ListRounds,
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

    @GetMapping("/{id}/rounds")
    fun rounds(
        @PathVariable id: String,
        @AuthenticationPrincipal principal: AuthenticatedAdmin,
    ): List<RoundResponse> = listRounds.list(TournamentId(id), principal.adminUserId).map(RoundResponse::fromOverview)

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
            settings = TournamentSettings(grandTichuBonus = settings.grandTichuBonus),
        )
}
