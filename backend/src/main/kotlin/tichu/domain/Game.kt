package tichu.domain

/**
 * One played game at a round table: the recorded raw facts (ADR 0004) of the
 * two playing teams — [team1], [team2], their base scores and each player's
 * [participants] — plus the [beschiss] marker. Created lazily on the first
 * submit for its slot; the pairing flow never pre-creates games.
 *
 * The natural key is `(tournamentId, roundNumber, tableNumber, gameNumber)`
 * (table numbers, not surrogate table ids — the pairing re-saves tables
 * wholesale). Everything derivable (success, double win, bonuses, totals,
 * victory points) is derived on read by [score], never stored (ADR 0004).
 */
data class Game(
    /** Surrogate database id; assigned by the sequence, internal only. */
    val id: Long?,
    val tournamentId: TournamentId,
    val roundNumber: Int,
    val tableNumber: Int,
    /** 1-based game number within the table (1–4; 1–6 at a trio table). */
    val gameNumber: Int,
    val team1: TeamId,
    val team2: TeamId,
    val baseScoreTeam1: Int,
    val baseScoreTeam2: Int,
    /** Playful table accusation; a pure marker with no score effect (v1 parity). */
    val beschiss: Boolean,
    /** Optimistic-locking counter: starts at 1 on create, increments on update. */
    val version: Int,
    val participants: List<GameParticipant>,
    /** Set when an organizer corrected this game at a completed table; null otherwise. */
    val correction: GameCorrection? = null,
) {
    init {
        require(team1 != team2) { "The two playing teams of a game must be distinct" }
        require(participants.size == 4) { "A game has exactly 4 participants, got ${participants.size}" }
    }

    /** The natural key this game is addressed by (the surrogate id stays internal). */
    val key: GameKey
        get() = GameKey(tournamentId, roundNumber, tableNumber, gameNumber)

    /** The recorded facts team-grouped, [team1] first — the shape [GameScoring] scores. */
    val teamResults: List<TeamResult>
        get() = listOf(teamResult(team1, baseScoreTeam1), teamResult(team2, baseScoreTeam2))

    /** Derives the outcome from the recorded facts — the one read and write share. */
    fun score(settings: TournamentSettings): GameOutcome = GameScoring.score(teamResults, settings)

    private fun teamResult(teamId: TeamId, baseScore: Int) = TeamResult(
        teamId = teamId,
        baseScore = baseScore,
        players = participants
            .filter { it.teamId == teamId }
            .map { PlayerResult(it.playerId, it.position, it.call, it.bombs) },
    )

    companion object {
        /**
         * A new, not-yet-persisted game at [key] from submitted team facts. The
         * facts are validated by [GameScoring] first, so an invalid submit fails
         * with a field-anchored [tichu.vocabulary.GameValidationException]
         * rather than this class's invariants. Id and version are assigned by the
         * repository adapter (version 1 on create, +1 per update).
         */
        fun record(
            key: GameKey,
            teams: List<TeamResult>,
            beschiss: Boolean,
            settings: TournamentSettings,
        ): Game {
            GameScoring.score(teams, settings)
            val (first, second) = teams
            return Game(
                id = null,
                tournamentId = key.tournamentId,
                roundNumber = key.roundNumber,
                tableNumber = key.tableNumber,
                gameNumber = key.gameNumber,
                team1 = first.teamId,
                team2 = second.teamId,
                baseScoreTeam1 = first.baseScore,
                baseScoreTeam2 = second.baseScore,
                beschiss = beschiss,
                version = 0,
                participants = teams.flatMap { team ->
                    team.players.map {
                        GameParticipant(it.playerId, team.teamId, it.position, it.call, it.bombs)
                    }
                },
            )
        }
    }
}

/**
 * One player's recorded facts in a [Game]: finish [position] (null when the
 * player never finished, i.e. lost a double win), the Tichu [call] made, and
 * the bomb count. [teamId] is denormalized — the player's team within this game.
 */
data class GameParticipant(
    val playerId: PlayerId,
    val teamId: TeamId,
    val position: Int?,
    val call: TichuCall,
    val bombs: Int,
)
