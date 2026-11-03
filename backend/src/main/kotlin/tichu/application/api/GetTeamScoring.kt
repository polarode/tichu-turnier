package tichu.application.api

import tichu.domain.PlayerId
import tichu.domain.ScheduledGame
import tichu.domain.TeamId
import tichu.domain.TournamentId

/**
 * The team-session read surface backing the scoring form (S5c, #101). A team
 * session knows only its own identity, so these reads resolve everything else
 * from the session: the one round the team is currently seated at, and the games
 * entered at that round table. The session — never the request — scopes the read;
 * a team may only ever read its own table.
 *
 * S5b (#100) shipped only the write endpoints plus identity; this port is the
 * read half the form needs (see ADR 0005).
 */
interface GetTeamScoring {

    /**
     * The team's current table: the single in-progress round it is seated at
     * (the highest-numbered round that seats the team — one round is open at a
     * time, Q6/ADR 0005). Fails as not-found when the team is not yet seated at
     * any round (the schedule appears once the tournament starts).
     */
    fun currentTable(principal: AuthenticatedTeam): TeamTable

    /**
     * The games entered at one round table, server-authoritative (raw facts +
     * derived outcome), ordered by game number. A game number absent from the
     * list has not been entered yet. The principal must be seated at the table.
     */
    fun games(principal: AuthenticatedTeam, tournamentId: TournamentId, roundNumber: Int, tableNumber: Int): List<RecordedGame>
}

/**
 * The team's view of the table it is seated at this round: the [tournamentId]
 * (the games endpoints address a game by tournament/round/table/game), which
 * round and table (the numbers the write endpoints address), the table's
 * [confirmation] state (games are read-only from the first confirmation),
 * what a grand Tichu call is worth
 * ([grandTichuBonus], so the form's live totals match the server), and the seated [teams]
 * (two, or three at the [trio] table) with their names and players — the matchup
 * label and the grid's roster — plus the [schedule] of who plays which game.
 */
data class TeamTable(
    val tournamentId: TournamentId,
    val roundNumber: Int,
    val tableNumber: Int,
    val confirmation: TableConfirmationState,
    val grandTichuBonus: Int,
    val teams: List<TeamTableTeam>,
    val trio: Boolean,
    val schedule: List<ScheduledGame>,
)

data class TeamTableTeam(
    val teamId: TeamId,
    val teamName: String,
    val players: List<TeamTablePlayer>,
)

data class TeamTablePlayer(
    val playerId: PlayerId,
    val name: String,
)
