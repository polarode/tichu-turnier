package com.example.template.infra.rest.web

import com.example.template.application.api.TeamTable
import com.example.template.application.api.TeamTablePlayer
import com.example.template.application.api.TeamTableTeam

/**
 * Wire format of the team-session scoring reads (S5c, #101; ADR 0005). The
 * current-table response tells the form which round/table it is entering, the
 * matchup and roster, whether the round is locked (read-only), and whether grand
 * Tichu is accepted (so the form can hide the option). The games list reuses
 * [RecordedGameDto] — the same shape the write endpoints return.
 */
data class TeamTableResponseDto(
    val tournamentId: String,
    val roundNumber: Int,
    val tableNumber: Int,
    val locked: Boolean,
    val allowGrandTichu: Boolean,
    val teams: List<TeamTableTeamDto>,
) {
    companion object {
        fun from(table: TeamTable): TeamTableResponseDto = TeamTableResponseDto(
            tournamentId = table.tournamentId.value,
            roundNumber = table.roundNumber,
            tableNumber = table.tableNumber,
            locked = table.locked,
            allowGrandTichu = table.allowGrandTichu,
            teams = table.teams.map(TeamTableTeamDto::from),
        )
    }
}

data class TeamTableTeamDto(
    val teamId: String,
    val teamName: String,
    val players: List<TeamTablePlayerDto>,
) {
    companion object {
        fun from(team: TeamTableTeam): TeamTableTeamDto = TeamTableTeamDto(
            teamId = team.teamId.value,
            teamName = team.teamName,
            players = team.players.map(TeamTablePlayerDto::from),
        )
    }
}

data class TeamTablePlayerDto(
    val playerId: String,
    val name: String,
) {
    companion object {
        fun from(player: TeamTablePlayer): TeamTablePlayerDto = TeamTablePlayerDto(
            playerId = player.playerId.value,
            name = player.name,
        )
    }
}
