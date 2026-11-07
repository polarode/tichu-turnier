package tichu.application.usecase

import tichu.application.api.GetPublicTournament
import tichu.application.api.PublicFinalRankingEntry
import tichu.application.api.PublicPlayer
import tichu.application.api.PublicRound
import tichu.application.api.PublicTable
import tichu.application.api.PublicTableTotal
import tichu.application.api.PublicTeam
import tichu.application.api.PublicTournamentDetail
import tichu.domain.Standings
import tichu.domain.TableTotals
import tichu.domain.TeamRepository
import tichu.domain.TournamentId
import tichu.domain.TournamentRepository
import tichu.domain.TournamentStatus
import tichu.vocabulary.NotFoundException

/**
 * Builds the public view of one tournament. A tournament that is not public, not in
 * a publicly listable status, or unknown fails with the same not-found, so a setup or
 * private tournament cannot be told apart from a missing one. Teams are read once and
 * looked up by id, so each table lists its teams in seat order without a per-team query.
 *
 * The rounds come from [PlayedRoundsReader] because a Published tournament also shows
 * results. Table Totals and the Final Ranking are computed only for a PUBLISHED tournament;
 * in every other status they stay null and the games are never turned into anything public.
 * A team's totals are null at a table that is not completed yet.
 */
class GetPublicTournamentQuery(
    private val tournamentRepository: TournamentRepository,
    private val teamRepository: TeamRepository,
    private val playedRounds: PlayedRoundsReader,
) : GetPublicTournament {

    override fun get(id: TournamentId): PublicTournamentDetail {
        val tournament = tournamentRepository.findById(id)
        if (tournament == null || !tournament.isVisibleToSpectators()) {
            throw NotFoundException("Tournament ${id.value} not found")
        }

        val published = tournament.status == TournamentStatus.PUBLISHED
        val teams = teamRepository.findAllByTournament(id)
        val teamsById = teams.associateBy { it.id }
        val rounds = playedRounds.of(id).sortedBy { it.round.number }

        val publicRounds = rounds.map { played ->
            val totals = if (published) TableTotals.of(played, tournament.settings) else emptyMap()
            PublicRound(
                number = played.round.number,
                tables = played.round.pairing.tables.map { table ->
                    val tableTotals = totals[table.id].orEmpty()
                    PublicTable(
                        tableNumber = table.id.value,
                        trio = table.isTrio,
                        teams = table.seatedTeams.map { teamId ->
                            val team = teamsById.getValue(teamId)
                            PublicTeam(
                                id = team.id,
                                name = team.name,
                                players = team.players.map { PublicPlayer(id = it.id, name = it.name) },
                                totals = tableTotals[teamId]?.let {
                                    PublicTableTotal(victoryPoints = it.victoryPoints, tichuPoints = it.tichuPoints)
                                },
                            )
                        },
                    )
                },
            )
        }

        val finalRanking = if (published) {
            Standings.compute(teams, rounds, tournament.settings).entries.map { entry ->
                PublicFinalRankingEntry(
                    rank = entry.rank,
                    teamId = entry.team.teamId,
                    teamName = entry.team.teamName,
                    gamesPlayed = entry.team.gamesPlayed,
                    victoryPoints = entry.team.victoryPoints,
                    tichuPoints = entry.team.tichuPoints,
                )
            }
        } else {
            null
        }

        return PublicTournamentDetail(
            id = tournament.id,
            name = tournament.name,
            status = tournament.status,
            rounds = publicRounds,
            finalRanking = finalRanking,
        )
    }
}
