package com.example.template.infra.rest.web

import com.example.template.application.api.StartedTournament
import com.example.template.domain.Round
import com.example.template.domain.Table
import com.example.template.domain.Tournament
import com.example.template.domain.TournamentSettings
import com.example.template.domain.TournamentStatus
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
    val allowGrandTichu: Boolean,
) {
    companion object {
        fun from(settings: TournamentSettings): TournamentSettingsResponse =
            TournamentSettingsResponse(allowGrandTichu = settings.allowGrandTichu)
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
    val allowGrandTichu: Boolean = true,
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
        fun from(round: Round): RoundResponse = RoundResponse(
            roundNumber = round.number,
            tables = round.pairing.tables.map(TableResponse::from),
        )
    }
}

data class TableResponse(
    val tableNumber: Int,
    val homeTeamId: String,
    val awayTeamId: String,
) {
    companion object {
        fun from(table: Table): TableResponse = TableResponse(
            tableNumber = table.id.value,
            homeTeamId = table.home.value,
            awayTeamId = table.away.value,
        )
    }
}
