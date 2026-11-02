package com.example.template.infra.rest.web

import com.example.template.application.api.TeamTable
import com.example.template.application.api.TeamTablePlayer
import com.example.template.application.api.TeamTableTeam

/**
 * Wire format of the team-session scoring reads (S5c, #101; ADR 0005). The
 * current-table response tells the form which round/table it is entering, the
 * matchup and roster, the table's confirmation [status] and [confirmedTeamIds]
 * (games are read-only unless PLAYING), and whether grand Tichu is accepted (so the form can hide the option). The games list reuses
 * [RecordedGameDto] — the same shape the write endpoints return.
 */
data class TeamTableResponseDto(
    val tournamentId: String,
    val roundNumber: Int,
    val tableNumber: Int,
    val status: String,
    val confirmedTeamIds: List<String>,
    val allowGrandTichu: Boolean,
    val teams: List<TeamTableTeamDto>,
    val trio: Boolean,
    /** Who plays each game at this table; the frontend never re-derives the trio schedule. */
    val schedule: List<ScheduledGameDto>,
) {
    companion object {
        fun from(table: TeamTable): TeamTableResponseDto = TeamTableResponseDto(
            tournamentId = table.tournamentId.value,
            roundNumber = table.roundNumber,
            tableNumber = table.tableNumber,
            status = table.confirmation.status.name,
            confirmedTeamIds = table.confirmation.confirmedTeamIds.map { it.value },
            allowGrandTichu = table.allowGrandTichu,
            teams = table.teams.map(TeamTableTeamDto::from),
            trio = table.trio,
            schedule = table.schedule.map(ScheduledGameDto::from),
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
