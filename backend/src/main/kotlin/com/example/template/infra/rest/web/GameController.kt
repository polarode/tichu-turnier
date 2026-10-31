package com.example.template.infra.rest.web

import com.example.template.application.api.AuthenticatedTeam
import com.example.template.application.api.RecordGame
import com.example.template.domain.TournamentId
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * REST adapter for the team scoring use cases (#100). The controller is
 * deliberately thin: it maps HTTP onto the inbound port and passes the
 * authenticated team as the principal. Seat checks, locking and validation
 * live in the use case. The team-session filter chain guarantees that
 * `principal` is the session's team — the request never names one.
 */
@RestController
@RequestMapping("/api/team/tournaments/{tournamentId}/rounds/{roundNumber}/tables/{tableNumber}/games")
class GameController(
    private val recordGame: RecordGame,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @PathVariable tournamentId: String,
        @PathVariable roundNumber: Int,
        @PathVariable tableNumber: Int,
        @Valid @RequestBody body: GameRequestDto,
        @AuthenticationPrincipal principal: AuthenticatedTeam,
    ): RecordedGameDto = RecordedGameDto.from(
        recordGame.create(TournamentId(tournamentId), roundNumber, tableNumber, body.toWriteRequest(), principal),
    )

    @PutMapping("/{gameNumber}")
    fun update(
        @PathVariable tournamentId: String,
        @PathVariable roundNumber: Int,
        @PathVariable tableNumber: Int,
        @PathVariable gameNumber: Int,
        @Valid @RequestBody body: GameRequestDto,
        @AuthenticationPrincipal principal: AuthenticatedTeam,
    ): RecordedGameDto = RecordedGameDto.from(
        recordGame.update(
            TournamentId(tournamentId),
            roundNumber,
            tableNumber,
            body.toWriteRequest().copy(gameNumber = gameNumber),
            principal,
        ),
    )
}
