package com.example.template.infra.rest.web

import com.example.template.application.api.RevealedTeamAccessLink
import com.example.template.application.api.TeamWriteRequest
import com.example.template.domain.Player
import com.example.template.domain.Team
import jakarta.validation.constraints.NotBlank
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Wire format of the team endpoints. The DTO carries the validation annotations;
 * the mapping to the transport-agnostic [TeamWriteRequest] is explicit so the
 * use cases never see HTTP types.
 */
data class TeamRequestDto(
    @field:NotBlank(message = "must not be blank")
    val name: String,
    @field:NotBlank(message = "must not be blank")
    val playerOneName: String,
    @field:NotBlank(message = "must not be blank")
    val playerTwoName: String,
) {
    fun toWriteRequest(): TeamWriteRequest = TeamWriteRequest(
        name = name,
        playerOneName = playerOneName,
        playerTwoName = playerTwoName,
    )
}

data class PlayerResponseDto(
    val id: String,
    val teamId: String,
    val name: String,
) {
    companion object {
        fun from(player: Player): PlayerResponseDto = PlayerResponseDto(
            id = player.id.value,
            teamId = player.teamId.value,
            name = player.name,
        )
    }
}

data class TeamAccessLinkResponseDto(
    val teamId: String,
    val tournamentId: String,
    val token: String,
    val link: String,
) {
    companion object {

        /** The only mapping that ever handles raw token material — returned exactly once, at reveal. */
        fun from(revealed: RevealedTeamAccessLink, frontendUrl: String): TeamAccessLinkResponseDto {
            val encodedToken = URLEncoder.encode(revealed.token, StandardCharsets.UTF_8)
            return TeamAccessLinkResponseDto(
                teamId = revealed.teamId.value,
                tournamentId = revealed.tournamentId.value,
                token = revealed.token,
                link = "${frontendUrl.trimEnd('/')}/team/login?token=$encodedToken",
            )
        }
    }
}

data class TeamResponseDto(
    val id: String,
    val tournamentId: String,
    val name: String,
    val players: List<PlayerResponseDto>,
) {
    companion object {
        fun from(team: Team): TeamResponseDto = TeamResponseDto(
            id = team.id.value,
            tournamentId = team.tournamentId.value,
            name = team.name,
            players = team.players.map(PlayerResponseDto::from),
        )
    }
}
