package tichu.infra.rest.web

import tichu.application.api.SeenGame
import tichu.application.api.TableConfirmationState
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull

/**
 * Wire format of the confirm request (S6a, #105): the games the team confirms,
 * as it last read them. The use case compares them with the stored games.
 */
data class ConfirmTableRequestDto(
    @field:NotNull
    @field:Valid
    val games: List<SeenGameDto>?,
) {
    fun toSeenGames(): List<SeenGame> = games.orEmpty().map { SeenGame(it.gameNumber!!, it.version!!) }
}

data class SeenGameDto(
    @field:NotNull
    val gameNumber: Int?,
    @field:NotNull
    val version: Int?,
)

/**
 * A table's confirmation state as the API shows it: the derived [status]
 * (PLAYING / CONFIRMING / COMPLETED) and the confirmed team ids in seat order.
 * The confirm/retract responses are exactly this; the current-table and admin
 * rounds reads carry the same two fields.
 */
data class TableConfirmationDto(
    val status: String,
    val confirmedTeamIds: List<String>,
) {
    companion object {
        fun from(state: TableConfirmationState): TableConfirmationDto = TableConfirmationDto(
            status = state.status.name,
            confirmedTeamIds = state.confirmedTeamIds.map { it.value },
        )
    }
}
