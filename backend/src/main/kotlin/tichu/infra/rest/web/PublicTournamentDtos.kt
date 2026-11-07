package tichu.infra.rest.web

import com.fasterxml.jackson.annotation.JsonInclude
import tichu.application.api.PublicFinalRankingEntry
import tichu.application.api.PublicPlayer
import tichu.application.api.PublicRound
import tichu.application.api.PublicTable
import tichu.application.api.PublicTableTotal
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
 * Wire representation of one public tournament's pairings (ADR 0008). The pairings are
 * always there. Once PUBLISHED, [finalRanking] is there too; it is omitted (not null)
 * before that. Never games, outcomes, table status or confirmations, in any status.
 */
data class PublicTournamentDetailResponse(
    val id: String,
    val name: String,
    val status: TournamentStatus,
    val rounds: List<PublicRoundResponse>,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val finalRanking: List<PublicFinalRankingEntryResponse>? = null,
) {
    companion object {
        fun from(detail: PublicTournamentDetail): PublicTournamentDetailResponse = PublicTournamentDetailResponse(
            id = detail.id.value,
            name = detail.name,
            status = detail.status,
            rounds = detail.rounds.map(PublicRoundResponse::from),
            finalRanking = detail.finalRanking?.map(PublicFinalRankingEntryResponse::from),
        )
    }
}

/** One line of the public Final Ranking; shared ranks repeat the number (ADR 0008, S9c). */
data class PublicFinalRankingEntryResponse(
    val rank: Int,
    val teamId: String,
    val teamName: String,
    val gamesPlayed: Int,
    val victoryPoints: Int,
    val tichuPoints: Int,
) {
    companion object {
        fun from(entry: PublicFinalRankingEntry): PublicFinalRankingEntryResponse = PublicFinalRankingEntryResponse(
            rank = entry.rank,
            teamId = entry.teamId.value,
            teamName = entry.teamName,
            gamesPlayed = entry.gamesPlayed,
            victoryPoints = entry.victoryPoints,
            tichuPoints = entry.tichuPoints,
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
    /** The team's Table Totals at this table; omitted (not null) unless published and completed. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val totals: PublicTableTotalResponse? = null,
) {
    companion object {
        fun from(team: PublicTeam): PublicTeamResponse = PublicTeamResponse(
            id = team.id.value,
            name = team.name,
            players = team.players.map(PublicPlayerResponse::from),
            totals = team.totals?.let(PublicTableTotalResponse::from),
        )
    }
}

data class PublicTableTotalResponse(
    val victoryPoints: Int,
    val tichuPoints: Int,
) {
    companion object {
        fun from(totals: PublicTableTotal): PublicTableTotalResponse = PublicTableTotalResponse(
            victoryPoints = totals.victoryPoints,
            tichuPoints = totals.tichuPoints,
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
