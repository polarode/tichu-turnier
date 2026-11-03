package tichu.infra.rest.web

import tichu.application.api.RecordedGame
import tichu.application.api.RoundOverview
import tichu.application.api.StartedTournament
import tichu.application.api.TableConfirmationState
import tichu.domain.Round
import tichu.domain.ScheduledGame
import tichu.domain.Table
import tichu.domain.Tournament
import tichu.domain.TournamentSettings
import tichu.domain.TournamentStatus
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.time.Instant

/**
 * Wire representations. The REST layer owns these; the domain never sees them.
 * Mapping domain -> response happens via [TournamentResponse.from].
 */
data class TournamentResponse(
    val id: String,
    val name: String,
    val description: String,
    val maxTeams: Int,
    val settings: TournamentSettingsResponse,
    val status: TournamentStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(tournament: Tournament): TournamentResponse = TournamentResponse(
            id = tournament.id.value,
            name = tournament.name,
            description = tournament.description,
            maxTeams = tournament.maxTeams,
            settings = TournamentSettingsResponse.from(tournament.settings),
            status = tournament.status,
            createdAt = tournament.createdAt,
            updatedAt = tournament.updatedAt,
        )
    }
}

data class TournamentSettingsResponse(
    val grandTichuBonus: Int,
) {
    companion object {
        fun from(settings: TournamentSettings): TournamentSettingsResponse =
            TournamentSettingsResponse(grandTichuBonus = settings.grandTichuBonus)
    }
}

/**
 * The editable tournament representation, shared by `POST /api/tournaments` and
 * `PUT /api/tournaments/{id}`: both send every editable field. The use case re-runs
 * the same validation and the status gate.
 */
data class TournamentWriteRequestBody(
    @field:NotBlank val name: String,
    val description: String = "",
    @field:NotNull @field:Positive val maxTeams: Int,
    @field:Valid val settings: TournamentSettingsRequestBody = TournamentSettingsRequestBody(),
)

data class TournamentSettingsRequestBody(
    /** Validated by the use case (100 or 200), so the error anchors to the field. */
    val grandTichuBonus: Int = TournamentSettings().grandTichuBonus,
)

/**
 * Wire representation of the start result: the activated tournament plus the newly
 * created round 1 with its table assignment.
 */
data class StartedTournamentResponse(
    val tournament: TournamentResponse,
    val round: RoundResponse,
) {
    companion object {
        fun from(started: StartedTournament): StartedTournamentResponse = StartedTournamentResponse(
            tournament = TournamentResponse.from(started.tournament),
            round = RoundResponse.from(started.round),
        )
    }
}

data class RoundResponse(
    val roundNumber: Int,
    val tables: List<TableResponse>,
) {
    companion object {
        /** A freshly created round (the start response): no games entered yet. */
        fun from(round: Round): RoundResponse = RoundResponse(
            roundNumber = round.number,
            tables = round.pairing.tables.map { TableResponse.from(it, games = emptyList(), TableConfirmationState.of(it)) },
        )

        /** The admin's rounds read: each table with its entered games (S5d, #102). */
        fun fromOverview(overview: RoundOverview): RoundResponse = RoundResponse(
            roundNumber = overview.round.number,
            tables = overview.tables.map { TableResponse.from(it.table, it.games, it.confirmation) },
        )
    }
}

data class TableResponse(
    val tableNumber: Int,
    /** The seated teams in seat order: two, or three at the trio table. */
    val teamIds: List<String>,
    val trio: Boolean,
    /** Games played here per round — the denominator of "x/n entered". */
    val gameCount: Int,
    /** Who plays each game; the server is the single source of the trio schedule. */
    val schedule: List<ScheduledGameDto>,
    /** The games entered so far, ordered by game number; absent numbers are not yet entered. */
    val games: List<RecordedGameDto>,
    /** The derived Table Status: PLAYING, CONFIRMING or COMPLETED (ADR 0006). */
    val status: String,
    /** The teams that confirmed, in seat order. */
    val confirmedTeamIds: List<String>,
) {
    companion object {
        fun from(table: Table, games: List<RecordedGame>, confirmation: TableConfirmationState): TableResponse = TableResponse(
            tableNumber = table.id.value,
            teamIds = table.seatedTeams.map { it.value },
            trio = table.isTrio,
            gameCount = table.gameCount,
            schedule = table.schedule.map(ScheduledGameDto::from),
            games = games.map(RecordedGameDto::from),
            status = confirmation.status.name,
            confirmedTeamIds = confirmation.confirmedTeamIds.map { it.value },
        )
    }
}

/** One game of a table's schedule: its number and the two teams playing it. */
data class ScheduledGameDto(
    val gameNumber: Int,
    val teamIds: List<String>,
) {
    companion object {
        fun from(game: ScheduledGame): ScheduledGameDto =
            ScheduledGameDto(game.gameNumber, game.playingTeams.map { it.value })
    }
}
