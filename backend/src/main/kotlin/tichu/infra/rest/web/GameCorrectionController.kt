package tichu.infra.rest.web

import tichu.application.api.AuthenticatedAdmin
import tichu.application.api.CorrectGame
import tichu.domain.TournamentId
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Driving adapter for the organizer's game correction (ADR 0010). It sits
 * beside the team [GameController] rather than in [TournamentController]: the
 * correction is a game write at a completed table, and it keeps the admin-side write
 * next to the team-side write it mirrors. The principal is the admin session; the
 * organizer check happens in [CorrectGame], so a stranger gets 404.
 */
@RestController
@RequestMapping("/api/tournaments/{tournamentId}/rounds/{roundNumber}/tables/{tableNumber}/games")
class GameCorrectionController(
    private val correctGame: CorrectGame,
) {

    /** Replaces a game's facts at a completed table, stamping the correcting organizer. */
    @PutMapping("/{gameNumber}")
    fun correct(
        @PathVariable tournamentId: String,
        @PathVariable roundNumber: Int,
        @PathVariable tableNumber: Int,
        @PathVariable gameNumber: Int,
        @Valid @RequestBody body: GameRequestDto,
        @AuthenticationPrincipal principal: AuthenticatedAdmin,
    ): RecordedGameDto = RecordedGameDto.from(
        correctGame.correct(
            TournamentId(tournamentId),
            roundNumber,
            tableNumber,
            body.toWriteRequest().copy(gameNumber = gameNumber),
            principal.adminUserId,
        ),
    )
}
