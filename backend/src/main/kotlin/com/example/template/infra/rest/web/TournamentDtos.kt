package com.example.template.infra.rest.web

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

data class CreateTournamentRequestBody(
    @field:NotBlank val name: String,
    val description: String = "",
    @field:NotNull @field:Positive val maxTeams: Int,
    @field:Valid val settings: TournamentSettingsRequestBody = TournamentSettingsRequestBody(),
)

data class TournamentSettingsRequestBody(
    val allowGrandTichu: Boolean = true,
)
