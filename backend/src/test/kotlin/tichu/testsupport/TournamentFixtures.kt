package tichu.testsupport

import tichu.domain.Game
import tichu.domain.GameKey
import tichu.domain.Player
import tichu.domain.PlayerId
import tichu.domain.PlayerResult
import tichu.domain.Team
import tichu.domain.TeamId
import tichu.domain.TeamResult
import tichu.domain.TichuCall
import tichu.domain.TournamentId
import tichu.domain.TournamentSettings

/**
 * Builders for tournament fixture data shared by use-case and integration tests.
 * Fixture data, not behaviour under test: ids follow one scheme (team `x` fields
 * players `x-1` and `x-2`) so games can reference players by their team id.
 */

/** A team [id] of [tournamentId] with its two players `id-1` and `id-2`. */
fun teamOf(tournamentId: TournamentId, id: String, name: String = "Team $id"): Team = Team(
    id = TeamId(id),
    tournamentId = tournamentId,
    name = name,
    players = listOf(
        Player(PlayerId("$id-1"), tournamentId, TeamId(id), "$name one"),
        Player(PlayerId("$id-2"), tournamentId, TeamId(id), "$name two"),
    ),
)

/**
 * A game at [key] decided by base score alone: [first] takes [firstBase] of the 100
 * base points and its players finish first and third; no calls, no bombs.
 */
fun baseScoreGame(key: GameKey, first: TeamId, second: TeamId, firstBase: Int): Game = Game.record(
    key,
    listOf(
        TeamResult(first, firstBase, listOf(
            PlayerResult(PlayerId("${first.value}-1"), 1, TichuCall.NONE, 0),
            PlayerResult(PlayerId("${first.value}-2"), 3, TichuCall.NONE, 0),
        )),
        TeamResult(second, 100 - firstBase, listOf(
            PlayerResult(PlayerId("${second.value}-1"), 2, TichuCall.NONE, 0),
            PlayerResult(PlayerId("${second.value}-2"), 4, TichuCall.NONE, 0),
        )),
    ),
    beschiss = false,
    settings = TournamentSettings(),
)
