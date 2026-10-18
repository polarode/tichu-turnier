package com.example.template.infra.rest.web

import com.example.template.application.api.CreateTeam
import com.example.template.application.api.GetTeam
import com.example.template.application.api.ListTeams
import com.example.template.application.api.AuthenticatedAdmin
import com.example.template.domain.TeamId
import com.example.template.domain.TournamentId
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
}
