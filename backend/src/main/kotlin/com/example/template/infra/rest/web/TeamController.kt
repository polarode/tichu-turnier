package com.example.template.infra.rest.web

import com.example.template.application.api.CreateTeam
import com.example.template.application.api.DeleteTeam
import com.example.template.application.api.GetTeam
import com.example.template.application.api.ListTeams
import com.example.template.application.api.AuthenticatedAdmin
import com.example.template.application.api.RevealedTeamAccessLink
import com.example.template.application.api.RotateTeamAccessLink
import com.example.template.application.api.RevealTeamAccessLink
import com.example.template.application.api.RevokeTeamAccessLink
import com.example.template.application.api.UpdateTeam
import com.example.template.domain.TeamId
import com.example.template.domain.TournamentId
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
 * REST adapter for the team use cases. The controller is deliberately thin: it maps
 * HTTP onto the inbound ports and passes the authenticated admin's id as the owner.
 * It never inspects tournament/team data itself — ownership and validation live in
 * the use cases.
 */
@RestController
@RequestMapping("/api/tournaments/{tournamentId}/teams")
class TeamController(
    private val createTeam: CreateTeam,
    private val listTeams: ListTeams,
    private val getTeam: GetTeam,
    private val updateTeam: UpdateTeam,
    private val deleteTeam: DeleteTeam,
    private val revealAccessLink: RevealTeamAccessLink,
    private val rotateAccessLink: RotateTeamAccessLink,
    private val revokeAccessLink: RevokeTeamAccessLink,
    private val frontendUrl: String,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @PathVariable tournamentId: String,
        @Valid @RequestBody body: TeamRequestDto,
        @AuthenticationPrincipal principal: AuthenticatedAdmin,
    ): TeamResponseDto = TeamResponseDto.from(
        createTeam.create(TournamentId(tournamentId), body.toWriteRequest(), principal.adminUserId),
    )

    @GetMapping
    fun list(
        @PathVariable tournamentId: String,
        @AuthenticationPrincipal principal: AuthenticatedAdmin,
    ): List<TeamResponseDto> =
        listTeams.list(TournamentId(tournamentId), principal.adminUserId).map(TeamResponseDto::from)

    @GetMapping("/{teamId}")
    fun get(
        @PathVariable tournamentId: String,
        @PathVariable teamId: String,
        @AuthenticationPrincipal principal: AuthenticatedAdmin,
    ): TeamResponseDto = TeamResponseDto.from(
        getTeam.get(TournamentId(tournamentId), TeamId(teamId), principal.adminUserId),
    )

    @PutMapping("/{teamId}")
    fun update(
        @PathVariable tournamentId: String,
        @PathVariable teamId: String,
        @Valid @RequestBody body: TeamRequestDto,
        @AuthenticationPrincipal principal: AuthenticatedAdmin,
    ): TeamResponseDto = TeamResponseDto.from(
        updateTeam.update(
            TournamentId(tournamentId),
            TeamId(teamId),
            body.toWriteRequest(),
            principal.adminUserId,
        ),
    )

    @DeleteMapping("/{teamId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable tournamentId: String,
        @PathVariable teamId: String,
        @AuthenticationPrincipal principal: AuthenticatedAdmin,
    ) {
        deleteTeam.delete(TournamentId(tournamentId), TeamId(teamId), principal.adminUserId)
    }

    // ------------------------------------------------------- access link lifecycle

    /** The one response that carries raw link material — returned exactly once. */
    @PostMapping("/{teamId}/access-link/reveal")
    fun revealAccessLink(
        @PathVariable tournamentId: String,
        @PathVariable teamId: String,
        @AuthenticationPrincipal principal: AuthenticatedAdmin,
    ): TeamAccessLinkResponseDto = TeamAccessLinkResponseDto.from(
        revealAccessLink.reveal(TournamentId(tournamentId), TeamId(teamId), principal.adminUserId),
        frontendUrl,
    )

    @PostMapping("/{teamId}/access-link/rotate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun rotateAccessLink(
        @PathVariable tournamentId: String,
        @PathVariable teamId: String,
        @AuthenticationPrincipal principal: AuthenticatedAdmin,
    ) {
        rotateAccessLink.rotate(TournamentId(tournamentId), TeamId(teamId), principal.adminUserId)
    }

    @PostMapping("/{teamId}/access-link/revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun revokeAccessLink(
        @PathVariable tournamentId: String,
        @PathVariable teamId: String,
        @AuthenticationPrincipal principal: AuthenticatedAdmin,
    ) {
        revokeAccessLink.revoke(TournamentId(tournamentId), TeamId(teamId), principal.adminUserId)
    }
}
