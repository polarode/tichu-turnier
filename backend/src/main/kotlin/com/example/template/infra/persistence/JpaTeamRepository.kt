package com.example.template.infra.persistence

import com.example.template.domain.Team
import com.example.template.domain.TeamId
import com.example.template.domain.TeamRepository
import com.example.template.domain.TournamentId

/**
 * Driven adapter implementing the [TeamRepository] port on top of Spring Data
 * JPA and PostgreSQL (schema managed by Flyway). A plain class — wired as a bean
 * by [PersistenceConfiguration].
 *
 * The team aggregate spans two tables: [save] writes the team row and both player
 * rows in the caller's transaction (the use case is `@Transactional`).
 */
class JpaTeamRepository(
    private val springDataTeamRepository: SpringDataTeamRepository,
    private val springDataPlayerRepository: SpringDataPlayerRepository,
) : TeamRepository {

    override fun save(team: Team): Team {
        val savedTeam = springDataTeamRepository.save(TeamEntity.from(team))
        val savedPlayers = team.players.map { springDataPlayerRepository.save(PlayerEntity.from(it)) }
        return savedTeam.toDomain(savedPlayers)
    }

    override fun findAllByTournament(tournamentId: TournamentId): List<Team> {
        val teams = springDataTeamRepository.findAllByTournamentId(tournamentId.value)
        return withPlayers(teams)
    }

    override fun findById(teamId: TeamId, tournamentId: TournamentId): Team? =
        springDataTeamRepository.findByIdAndTournamentId(teamId.value, tournamentId.value)
            ?.let { withPlayers(listOf(it)) }
            ?.firstOrNull()

    override fun delete(teamId: TeamId) {
        springDataTeamRepository.deleteById(teamId.value)
    }

    /** Loads the players of each team and reassembles the domain aggregates. */
    private fun withPlayers(teams: List<TeamEntity>): List<Team> {
        val teamIds = teams.map { it.id }
        val playersByTeam = springDataPlayerRepository.findAllByTeamIdIn(teamIds).groupBy { it.teamId }
        return teams.map { team -> team.toDomain(playersByTeam[team.id].orEmpty()) }
    }
}
