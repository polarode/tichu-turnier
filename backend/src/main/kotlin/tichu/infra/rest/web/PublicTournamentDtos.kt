package tichu.infra.rest.web

import tichu.application.api.PublicPlayer
import tichu.application.api.PublicRound
import tichu.application.api.PublicTable
import tichu.application.api.PublicTeam
import tichu.application.api.PublicTournament
import tichu.application.api.PublicTournamentDetail
import tichu.domain.TournamentStatus

/**
 * Wire representation of one public tournament. Public facts only (ADR 0008): no owner,
 * no tokens or credentials, no access-link state.
 */
data class PublicTournamentResponse(
    val id: String,
    val name: String,
    val status: TournamentStatus,
    val teamCount: Int,
) {
    companion object {
        fun from(tournament: PublicTournament): PublicTournamentResponse = PublicTournamentResponse(
            id = tournament.id.value,
            name = tournament.name,
            status = tournament.status,
            teamCount = tournament.teamCount,
        )
    }
}

/**
 * Wire representation of one public tournament's pairings (ADR 0008). Pairings only: no
 * games, results, table status or confirmations, in any status.
 */
data class PublicTournamentDetailResponse(
    val id: String,
    val name: String,
    val status: TournamentStatus,
    val rounds: List<PublicRoundResponse>,
) {
    companion object {
        fun from(detail: PublicTournamentDetail): PublicTournamentDetailResponse = PublicTournamentDetailResponse(
            id = detail.id.value,
            name = detail.name,
            status = detail.status,
            rounds = detail.rounds.map(PublicRoundResponse::from),
        )
    }
}

data class PublicRoundResponse(
    val number: Int,
    val tables: List<PublicTableResponse>,
) {
    companion object {
        fun from(round: PublicRound): PublicRoundResponse = PublicRoundResponse(
            number = round.number,
            tables = round.tables.map(PublicTableResponse::from),
        )
    }
}

data class PublicTableResponse(
    val tableNumber: Int,
    val trio: Boolean,
    val teams: List<PublicTeamResponse>,
) {
    companion object {
        fun from(table: PublicTable): PublicTableResponse = PublicTableResponse(
            tableNumber = table.tableNumber,
            trio = table.trio,
            teams = table.teams.map(PublicTeamResponse::from),
        )
    }
}

data class PublicTeamResponse(
    val id: String,
    val name: String,
    val players: List<PublicPlayerResponse>,
) {
    companion object {
        fun from(team: PublicTeam): PublicTeamResponse = PublicTeamResponse(
            id = team.id.value,
            name = team.name,
            players = team.players.map(PublicPlayerResponse::from),
        )
    }
}

data class PublicPlayerResponse(
    val id: String,
    val name: String,
) {
    companion object {
        fun from(player: PublicPlayer): PublicPlayerResponse = PublicPlayerResponse(
            id = player.id.value,
            name = player.name,
        )
    }
}
