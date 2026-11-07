package tichu.application.usecase

import tichu.application.api.GetPublicTournament
import tichu.application.api.PublicPlayer
import tichu.application.api.PublicRound
import tichu.application.api.PublicTable
import tichu.application.api.PublicTeam
import tichu.application.api.PublicTournamentDetail
import tichu.domain.RoundRepository
import tichu.domain.TeamRepository
import tichu.domain.TournamentId
import tichu.domain.TournamentRepository
import tichu.vocabulary.NotFoundException

/**
 * Builds the public pairings of one tournament. A tournament that is not public, not in
 * a publicly listable status, or unknown fails with the same not-found, so a setup or
 * private tournament cannot be told apart from a missing one. Teams are read once and
 * looked up by id, so each table lists its teams in seat order without a per-team query.
 */
class GetPublicTournamentQuery(
    private val tournamentRepository: TournamentRepository,
    private val teamRepository: TeamRepository,
    private val roundRepository: RoundRepository,
) : GetPublicTournament {

    override fun get(id: TournamentId): PublicTournamentDetail {
        val tournament = tournamentRepository.findById(id)
        if (tournament == null || !tournament.isVisibleToSpectators()) {
            throw NotFoundException("Tournament ${id.value} not found")
        }

        val teamsById = teamRepository.findAllByTournament(id).associateBy { it.id }
        val rounds = roundRepository.findByTournament(id).sortedBy { it.number }.map { round ->
            PublicRound(
                number = round.number,
                tables = round.pairing.tables.map { table ->
                    PublicTable(
                        tableNumber = table.id.value,
                        trio = table.isTrio,
                        teams = table.seatedTeams.map { teamId ->
                            val team = teamsById.getValue(teamId)
                            PublicTeam(
                                id = team.id,
                                name = team.name,
                                players = team.players.map { PublicPlayer(id = it.id, name = it.name) },
                            )
                        },
                    )
                },
            )
        }

        return PublicTournamentDetail(
            id = tournament.id,
            name = tournament.name,
            status = tournament.status,
            rounds = rounds,
        )
    }
}
