package tichu.application.usecase

import tichu.domain.ForfeitGame
import tichu.domain.Game
import tichu.domain.PlayedRound
import tichu.domain.Round
import tichu.domain.Table
import tichu.domain.TeamId
import tichu.domain.TeamRepository
import tichu.domain.TournamentId
import tichu.domain.Withdrawal

/*
 * The withdrawal steps the table-level use cases share (ADR 0011): which teams of a
 * tournament withdrew, and which games of a table are Forfeit Games. Kept here once,
 * so confirm, record, correct, the reads and the round gates all count alike.
 */

/** The tournament's withdrawn teams with their withdrawal; a team that plays on is absent. */
internal fun withdrawalsOf(teamRepository: TeamRepository, tournamentId: TournamentId): Map<TeamId, Withdrawal> =
    teamRepository.findAllByTournament(tournamentId)
        .mapNotNull { team -> team.withdrawal?.let { team.id to it } }
        .toMap()

/**
 * The Forfeit Games of [table] in [round]: its scheduled games that were never entered
 * ([games] are the table's entered games) and include a team withdrawn in this round.
 */
internal fun tableForfeits(
    round: Round,
    table: Table,
    games: List<Game>,
    withdrawals: Map<TeamId, Withdrawal>,
): List<ForfeitGame> = PlayedRound(round, games, emptyMap(), withdrawals).forfeits
    .filter { it.tableNumber == table.id.value }
