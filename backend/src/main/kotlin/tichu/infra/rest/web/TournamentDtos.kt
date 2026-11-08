package tichu.infra.rest.web

import tichu.application.api.OrganizedTournament
import tichu.application.api.RecordedGame
import tichu.application.api.RoundOverview
import tichu.application.api.StartedTournament
import tichu.application.api.TableConfirmationState
import tichu.application.api.TeamStatisticsEntry
import tichu.application.api.TournamentStatistics
import tichu.domain.Highlight
import tichu.domain.HighlightMetric
import tichu.domain.Round
import tichu.domain.ScheduledGame
import tichu.domain.StandingsEntry
import tichu.domain.Table
import tichu.domain.TournamentSettings
import tichu.domain.TeamTrend
import tichu.domain.TournamentStatus
import tichu.domain.TrendPoint
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
    val publicVisibility: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    /** The caller's role on the tournament: `LEAD` or `CO` (#131). */
    val organizerRole: String,
    /** The email of the tournament's lead organizer (#131). */
    val leadOrganizerEmail: String,
) {
    companion object {
        fun from(view: OrganizedTournament): TournamentResponse {
            val tournament = view.tournament
            return TournamentResponse(
                id = tournament.id.value,
                name = tournament.name,
                description = tournament.description,
                maxTeams = tournament.maxTeams,
                settings = TournamentSettingsResponse.from(tournament.settings),
                status = tournament.status,
                publicVisibility = tournament.publicVisibility,
                createdAt = tournament.createdAt,
                updatedAt = tournament.updatedAt,
                organizerRole = view.role.name,
                leadOrganizerEmail = view.leadOrganizerEmail,
            )
        }
    }
}

data class TournamentSettingsResponse(
    val grandTichuBonus: Int,
    val doubleWinBonus: Int,
    val totalRounds: Int,
) {
    companion object {
        fun from(settings: TournamentSettings): TournamentSettingsResponse = TournamentSettingsResponse(
            grandTichuBonus = settings.grandTichuBonus,
            doubleWinBonus = settings.doubleWinBonus,
            totalRounds = settings.totalRounds,
        )
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
    /** Validated by the use case (100 or 200), so the error anchors to the field. */
    val doubleWinBonus: Int = TournamentSettings().doubleWinBonus,
    /** Validated by the use case (1–20), so the error anchors to the field. */
    val totalRounds: Int = TournamentSettings().totalRounds,
)

/** The body of `PUT /api/tournaments/{id}/total-rounds`; the range is validated by the use case. */
data class TotalRoundsRequestBody(
    @field:NotNull val totalRounds: Int,
)

/** The body of `PUT /api/tournaments/{id}/public-visibility`; allowed in every status. */
data class PublicVisibilityRequestBody(
    val publicVisibility: Boolean,
)

/** One line of the admin standings read, in standings order. */
data class StandingsEntryResponse(
    val rank: Int,
    val teamId: String,
    val teamName: String,
    val gamesPlayed: Int,
    val victoryPoints: Int,
    val tichuPoints: Int,
) {
    companion object {
        fun from(entry: StandingsEntry): StandingsEntryResponse = StandingsEntryResponse(
            rank = entry.rank,
            teamId = entry.team.teamId.value,
            teamName = entry.team.teamName,
            gamesPlayed = entry.team.gamesPlayed,
            victoryPoints = entry.team.victoryPoints,
            tichuPoints = entry.team.tichuPoints,
        )
    }
}

/** Wire representation of the statistics read, identical for the organizer and spectators: every team, the Highlights and the Team Trends. */
data class TournamentStatisticsResponse(
    val teams: List<TeamStatisticsResponse>,
    val highlights: List<HighlightResponse>,
    val trends: List<TeamTrendResponse>,
) {
    companion object {
        fun from(statistics: TournamentStatistics): TournamentStatisticsResponse = TournamentStatisticsResponse(
            teams = statistics.teams.map(TeamStatisticsResponse::from),
            highlights = statistics.highlights.map(HighlightResponse::from),
            trends = statistics.trends.map(TeamTrendResponse::from),
        )
    }
}

/**
 * One team's Team Statistics with its name; `bombsPerGame` is null when the team has no completed table yet.
 * `goodHorseRatio` is the summed Tichu Points over the summed Victory Points, null when the team has no Victory Points yet.
 */
data class TeamStatisticsResponse(
    val teamId: String,
    val teamName: String,
    val successfulSmallTichu: Int,
    val successfulGrandTichu: Int,
    val bombCount: Int,
    val bombsPerGame: Double?,
    val doubleWins: Int,
    val tichuCalls: Int,
    val destroyedTichus: Int,
    val beschissGames: Int,
    val goodHorseRatio: Double?,
) {
    companion object {
        fun from(entry: TeamStatisticsEntry): TeamStatisticsResponse = TeamStatisticsResponse(
            teamId = entry.statistics.teamId.value,
            teamName = entry.teamName,
            successfulSmallTichu = entry.statistics.successfulSmallTichu,
            successfulGrandTichu = entry.statistics.successfulGrandTichu,
            bombCount = entry.statistics.bombCount,
            bombsPerGame = entry.statistics.bombsPerGame,
            doubleWins = entry.statistics.doubleWins,
            tichuCalls = entry.statistics.tichuCalls,
            destroyedTichus = entry.statistics.destroyedTichus,
            beschissGames = entry.statistics.beschissGames,
            goodHorseRatio = entry.statistics.goodHorseRatio,
        )
    }
}

/** One Highlight: the statistic [metric] led by [teamIds] with [value]. */
data class HighlightResponse(
    val metric: HighlightMetric,
    val value: Double,
    val teamIds: List<String>,
) {
    companion object {
        fun from(highlight: Highlight): HighlightResponse = HighlightResponse(
            metric = highlight.metric,
            value = highlight.value,
            teamIds = highlight.teams.map { it.value },
        )
    }
}

/** One team's Team Trend: its cumulative victory points after each completed round. */
data class TeamTrendResponse(
    val teamId: String,
    val points: List<TrendPointResponse>,
) {
    companion object {
        fun from(trend: TeamTrend): TeamTrendResponse = TeamTrendResponse(
            teamId = trend.teamId.value,
            points = trend.points.map(TrendPointResponse::from),
        )
    }
}

/** One point of a Team Trend. */
data class TrendPointResponse(
    val roundNumber: Int,
    val victoryPoints: Int,
) {
    companion object {
        fun from(point: TrendPoint): TrendPointResponse = TrendPointResponse(
            roundNumber = point.roundNumber,
            victoryPoints = point.victoryPoints,
        )
    }
}

/**
 * Wire representation of the start result: the activated tournament plus the newly
 * created round 1 with its table assignment.
 */
data class StartedTournamentResponse(
    val tournament: TournamentResponse,
    val round: RoundResponse,
) {
    companion object {
        /** [tournament] is the caller's view after the start, so it carries the caller's role. */
        fun from(tournament: OrganizedTournament, started: StartedTournament): StartedTournamentResponse =
            StartedTournamentResponse(
                tournament = TournamentResponse.from(tournament),
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
