package com.example.template.infra.rest.web

import com.example.template.application.api.AuthenticatedTeam
import com.example.template.application.api.ConfirmTable
import com.example.template.domain.TableKey
import com.example.template.domain.TournamentId
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * REST adapter for the per-table confirmation workflow (S6a, #105; ADR 0006).
 * Thin: maps HTTP onto [ConfirmTable] and passes the session's team as the
 * principal; seat checks, the confirm gate and the freeze live in the use case.
 * Both verbs answer 200 with the table's confirmation state, also when the
 * request changed nothing.
 */
@RestController
@RequestMapping("/api/team/tournaments/{tournamentId}/rounds/{roundNumber}/tables/{tableNumber}/confirmation")
class TableConfirmationController(
    private val confirmTable: ConfirmTable,
) {

    @PutMapping
    fun confirm(
        @PathVariable tournamentId: String,
        @PathVariable roundNumber: Int,
        @PathVariable tableNumber: Int,
        @Valid @RequestBody body: ConfirmTableRequestDto,
        @AuthenticationPrincipal principal: AuthenticatedTeam,
    ): TableConfirmationDto = TableConfirmationDto.from(
        confirmTable.confirm(TableKey(TournamentId(tournamentId), roundNumber, tableNumber), body.toSeenGames(), principal),
    )

    @DeleteMapping
    fun retract(
        @PathVariable tournamentId: String,
        @PathVariable roundNumber: Int,
        @PathVariable tableNumber: Int,
        @AuthenticationPrincipal principal: AuthenticatedTeam,
    ): TableConfirmationDto = TableConfirmationDto.from(
        confirmTable.retract(TableKey(TournamentId(tournamentId), roundNumber, tableNumber), principal),
    )
}
