package tichu.infra.rest.web

import tichu.application.api.AddCoOrganizer
import tichu.application.api.AuthenticatedAdmin
import tichu.application.api.ListOrganizers
import tichu.domain.TournamentId
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
 * REST adapter for the organizer list (#131). Any organizer may read it; only the lead may
 * add a co-organizer, which the use case enforces. Thin, like the team controller.
 */
@RestController
@RequestMapping("/api/tournaments/{tournamentId}/organizers")
class OrganizersController(
    private val listOrganizers: ListOrganizers,
    private val addCoOrganizer: AddCoOrganizer,
) {

    @GetMapping
    fun list(
        @PathVariable tournamentId: String,
        @AuthenticationPrincipal principal: AuthenticatedAdmin,
    ): OrganizersResponse = OrganizersResponse.from(
        listOrganizers.list(TournamentId(tournamentId), principal.adminUserId),
    )

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun add(
        @PathVariable tournamentId: String,
        @Valid @RequestBody body: AddCoOrganizerRequestBody,
        @AuthenticationPrincipal principal: AuthenticatedAdmin,
    ): OrganizersResponse = OrganizersResponse.from(
        addCoOrganizer.add(TournamentId(tournamentId), body.email, principal.adminUserId),
    )
}
